package no.nav.k9.los.nyoppgavestyring.forvaltning

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.kodeverk.behandling.FagsakYtelseType
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.modell.Fagsystem
import no.nav.k9.los.domene.repository.BehandlingProsessEventK9Repository
import no.nav.k9.los.domene.repository.BehandlingProsessEventKlageRepository
import no.nav.k9.los.domene.repository.BehandlingProsessEventTilbakeRepository
import no.nav.k9.los.domene.repository.PunsjEventK9Repository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos.K9SakTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Repository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepositoryTxWrapper
import no.nav.k9.los.utils.LosObjectMapper
import org.koin.ktor.ext.inject
import java.util.*


fun Route.forvaltningApis() {

    val k9sakEventRepository by inject<BehandlingProsessEventK9Repository>()
    val k9tilbakeEventRepository by inject<BehandlingProsessEventTilbakeRepository>()
    val k9klageEventRepository by inject<BehandlingProsessEventKlageRepository>()
    val k9PunsjEventK9Repository by inject<PunsjEventK9Repository>()
    val oppgaveRepositoryTxWrapper by inject<OppgaveRepositoryTxWrapper>()
    val oppgaveTypeRepository by inject<OppgavetypeRepository>()
    val k9SakTilLosHistorikkvaskTjeneste by inject<K9SakTilLosHistorikkvaskTjeneste>()
    val reservasjonV3Repository by inject<ReservasjonV3Repository>()
    val objectMapper = LosObjectMapper.prettyInstance
    val transactionalManager by inject<TransactionalManager>()

    get("/eventer/{system}/{eksternId}") {
        val fagsystem = Fagsystem.fraKode(call.parameters["system"]!!)
        val eksternId = call.parameters["eksternId"]
        when (fagsystem) {
            Fagsystem.K9SAK -> {
                val k9SakModell = k9sakEventRepository.hent(UUID.fromString(eksternId))
                if (k9SakModell.eventer.isEmpty()) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    val eventerIkkeSensitive = k9SakModell.eventer.map { event -> K9SakEventIkkeSensitiv(event) }
                    call.respond(objectMapper.writeValueAsString(eventerIkkeSensitive))
                }
            }

            Fagsystem.K9TILBAKE -> {
                val k9TilbakeModell = k9tilbakeEventRepository.hent(UUID.fromString(eksternId))
                val eventerIkkeSensitive = k9TilbakeModell.eventer.map { event -> K9TilbakeEventIkkeSensitiv(event) }
                objectMapper.writeValueAsString(eventerIkkeSensitive)
            }

            Fagsystem.K9KLAGE -> {
                val k9KlageModell = k9klageEventRepository.hent(UUID.fromString(eksternId))
                val eventerIkkeSensitive = k9KlageModell.eventer.map { event -> K9KlageEventIkkeSensitiv(event) }
                objectMapper.writeValueAsString(eventerIkkeSensitive)
            }

            Fagsystem.PUNSJ -> {
                val k9PunsjModell = k9PunsjEventK9Repository.hent(UUID.fromString(eksternId))
                val eventerIkkeSensitive = k9PunsjModell.eventer.map { event -> K9PunsjEventIkkeSensitiv(event) }
                objectMapper.writeValueAsString(eventerIkkeSensitive)
            }

            Fagsystem.OMSORGSPENGER -> HttpStatusCode.NotImplemented
            Fagsystem.FPTILBAKE -> HttpStatusCode.NotImplemented
        }
    }

    get("/oppgaveV3/{omrade}/{oppgavetype}/{oppgaveEksternId}") {
        val område = call.parameters["omrade"]!!
        val oppgavetype = call.parameters["oppgavetype"]!!
        val oppgaveEksternId = call.parameters["oppgaveEksternId"]!!

        val oppgaveTidsserie =
            oppgaveRepositoryTxWrapper.hentOppgaveTidsserie(område, oppgavetype, oppgaveEksternId)
        if (oppgaveTidsserie.isEmpty()) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            val tidsserieIkkeSensitiv = oppgaveTidsserie.map { oppgave -> OppgaveIkkeSensitiv(oppgave) }
            call.respond(objectMapper.writeValueAsString(tidsserieIkkeSensitiv))
        }
    }

    get("/oppgaveV3/{omrade}/{oppgavetype}/{oppgaveEksternId}/historikkvask") {
        val område = call.parameters["omrade"]!!
        val oppgavetype = call.parameters["oppgavetype"]!!
        val oppgaveEksternId = call.parameters["oppgaveEksternId"]!!

        when(område) {
            "K9" -> {
                when(oppgavetype) {
                    "k9sak" -> {
                        k9SakTilLosHistorikkvaskTjeneste.vaskOppgaveForBehandlingUUID(UUID.fromString(oppgaveEksternId))
                        call.respond(HttpStatusCode.NoContent)
                    }
                    else -> call.respond(HttpStatusCode.NotImplemented, "Støtter ikke historikkvask på oppgavetype: $oppgavetype for område: $område")
                }
            }
            else -> call.respond(HttpStatusCode.NotImplemented, "Støtter ikke historikkvask på område: $område")
        }
    }

    get("/oppgaveV3/{omrade}/{oppgavetype}/{oppgaveEksternId}/reservasjoner") {
        val område = call.parameters["omrade"]!!
        val oppgavetypeEksternId = call.parameters["oppgavetype"]!!
        val oppgaveEksternId = call.parameters["oppgaveEksternId"]!!

        try {
            oppgaveTypeRepository.hentOppgavetype(område, oppgavetypeEksternId)
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.NotFound, e.message.toString())
            return@get
        }

        val oppgave = oppgaveRepositoryTxWrapper.hentOppgave(område, oppgaveEksternId)
        val reservasjonsnøkkel = utledReservasjonsnøkkel(oppgave, false)
        val reservasjonsnøkkel_beslutter = utledReservasjonsnøkkel(oppgave, true)
        val reservasjonerOrdinær = transactionalManager.transaction { tx ->
            reservasjonV3Repository.hentReservasjonTidslinjeMedEndringer(reservasjonsnøkkel, tx)
        }
        val reservasjonerBeslutter = transactionalManager.transaction { tx ->
            reservasjonV3Repository.hentReservasjonTidslinjeMedEndringer(reservasjonsnøkkel_beslutter, tx)
        }

        val reservasjonerSamlet = (reservasjonerOrdinær + reservasjonerBeslutter).sortedBy { it.reservasjonOpprettet }
        call.respond(objectMapper.writeValueAsString(reservasjonerSamlet))
    }

}

fun utledReservasjonsnøkkel(oppgave: Oppgave, erTilBeslutter: Boolean): String {
    return when (FagsakYtelseType.fraKode(oppgave.hentVerdi("ytelsestype"))) {
        FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
        FagsakYtelseType.PLEIEPENGER_NÆRSTÅENDE,
        FagsakYtelseType.OMSORGSPENGER_KS,
        FagsakYtelseType.OMSORGSPENGER_AO,
        FagsakYtelseType.OPPLÆRINGSPENGER -> lagNøkkelPleietrengendeAktør(oppgave, erTilBeslutter)
        else -> lagNøkkelAktør(oppgave, erTilBeslutter)
    }
}

fun lagNøkkelPleietrengendeAktør(oppgave: Oppgave, tilBeslutter: Boolean): String {
    return if (tilBeslutter)
        "K9_b_${oppgave.hentVerdi("ytelsestype")}_${oppgave.hentVerdi("pleietrengendeAktorId")}_beslutter"
    else {
        "K9_b_${oppgave.hentVerdi("ytelsestype")}_${oppgave.hentVerdi("pleietrengendeAktorId")}"
    }
}

fun lagNøkkelAktør(oppgave: Oppgave, tilBeslutter: Boolean): String {
    return if (tilBeslutter) {
        "K9_b_${oppgave.hentVerdi("ytelsestype")}_${oppgave.hentVerdi("aktorId")}_beslutter"
    } else {
        "K9_b_${oppgave.hentVerdi("ytelsestype")}_${oppgave.hentVerdi("aktorId")}"
    }
}
