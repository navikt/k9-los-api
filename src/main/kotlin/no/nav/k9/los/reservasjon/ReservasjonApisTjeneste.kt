package no.nav.k9.los.reservasjon

import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.abac.Action
import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstMedOmråde
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.utils.leggTilDagerHoppOverHelg
import no.nav.k9.los.kodeverk.BehandlingType
import no.nav.k9.los.kodeverk.FagsakYtelseType
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.enkeltoppslag.AktivOppgaveOppslag
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto
import no.nav.k9.los.oppgaveuthenting.sammendrag.OppgaveSammendragDtoBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.LocalTime

class ReservasjonApisTjeneste(
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste,
    private val transactionalManager: TransactionalManager,
    private val reservasjonV3DtoBuilder: ReservasjonV3DtoBuilder,
    private val aktivOppgaveOppslag: AktivOppgaveOppslag,
    private val pepClient: IPepClient,
    private val oppgaveSammendragDtoBuilder: OppgaveSammendragDtoBuilder,
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger("OppgaveApisTjeneste")
    }

    suspend fun reserverOppgave(
        innloggetBruker: Saksbehandler,
        oppgaveNøkkel: OppgaveNøkkelDto,
        skjermet: Boolean,
        brukerkontekst: BrukerkontekstMedOmråde,
    ): OppgaveStatusDto {
        brukerkontekst.krevOmråde(oppgaveNøkkel.områdeEksternId)
        if (!brukerkontekst.harTilgangTilReserveringAvOppgaver) {
            throw ManglerTilgangException("Mangler tilgang til reservering")
        }
        val reserverFra = LocalDateTime.now()

        val reserverForSaksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(
            innloggetBruker.navident!!, skjermet
        )!!

        val reservasjonV3 = transactionalManager.transactionSuspend { tx ->
            val oppgave = aktivOppgaveOppslag.hentAktivOppgave(
                oppgaveNøkkel.oppgaveEksternId,
                oppgaveNøkkel.oppgaveTypeEksternId,
                brukerkontekst.område,
                tx
            )
            brukerkontekst.krevOmråde(oppgave.oppgavetype.område.tilOmrådeEnum())

            reservasjonV3Tjeneste.kontrollerReservasjonstilgang(
                oppgave.reservasjonsnøkkel, innloggetBruker, brukerkontekst,
                mottaker = reserverForSaksbehandler, nyReservasjon = true,
            )
            reservasjonV3Tjeneste.forsøkReservasjonOgReturnerAktiv(
                reservasjonsnøkkel = oppgave.reservasjonsnøkkel,
                reserverForId = reserverForSaksbehandler.id,
                gyldigFra = reserverFra,
                utføresAvId = innloggetBruker.id,
                kommentar = null,
                gyldigTil = reserverFra.leggTilDagerHoppOverHelg(2),
                tx = tx
            )
        }

        val saksbehandlerSomHarReservasjon =
            saksbehandlerRepository.finnSaksbehandlerMedId(reservasjonV3.reservertAv)!!
        return OppgaveStatusDto(reservasjonV3, innloggetBruker, saksbehandlerSomHarReservasjon)
    }

    suspend fun endreReservasjoner(
        reservasjonEndringDto: List<ReservasjonEndringDto>,
        innloggetBruker: Saksbehandler,
        skjermet: Boolean,
        brukerkontekst: BrukerkontekstMedOmråde,
    ) {
        // Hele batchen kontrolleres før den første reservasjonen endres.
        val kontrollerteEndringer = reservasjonEndringDto.map {
            val nøkkel = finnReservasjonsnøkkel(it.reservasjonsnøkkel, it.oppgaveNøkkel, brukerkontekst)
            val mottaker = it.brukerIdent?.let { ident ->
                saksbehandlerRepository.finnSaksbehandlerMedIdent(ident, skjermet)
                    ?: throw ManglerTilgangException("Fant ikke mottaker i valgt skjerming")
            }
            reservasjonV3Tjeneste.kontrollerReservasjonstilgang(nøkkel, innloggetBruker, brukerkontekst, mottaker)
            Triple(it, nøkkel, mottaker)
        }
        kontrollerteEndringer.forEach { (endring, nøkkel, mottaker) ->
            reservasjonV3Tjeneste.endreReservasjon(
                reservasjonsnøkkel = nøkkel,
                endretAvBrukerId = innloggetBruker.id,
                nyTildato = endring.reserverTil?.atTime(LocalTime.MAX),
                nySaksbehandlerId = mottaker?.id,
                kommentar = endring.begrunnelse,
            )
        }
    }

    suspend fun forlengReservasjon(
        forlengReservasjonDto: ForlengReservasjonDto,
        innloggetBruker: Saksbehandler,
        brukerkontekst: BrukerkontekstMedOmråde,
    ): ReservasjonV3Dto {
        val reservasjonsnøkkel = finnReservasjonsnøkkel(
            forlengReservasjonDto.reservasjonsnøkkel, forlengReservasjonDto.oppgaveNøkkel, brukerkontekst
        )
        reservasjonV3Tjeneste.kontrollerReservasjonstilgang(reservasjonsnøkkel, innloggetBruker, brukerkontekst)

        val forlengetReservasjon =
            reservasjonV3Tjeneste.forlengReservasjon(
                reservasjonsnøkkel = reservasjonsnøkkel,
                nyTildato = forlengReservasjonDto.nyTilDato,
                utførtAvBrukerId = innloggetBruker.id,
                kommentar = forlengReservasjonDto.kommentar
            )

        val reservertAv =
            saksbehandlerRepository.finnSaksbehandlerMedId(forlengetReservasjon.reservasjonV3.reservertAv)!!
        log.info("forlengReservasjon: ${forlengetReservasjon.reservasjonV3}, reservertAv: $reservertAv")

        return reservasjonV3DtoBuilder.byggReservasjonV3Dto(forlengetReservasjon, reservertAv, brukerkontekst)
    }

    suspend fun overførReservasjon(
        params: FlyttReservasjonDto,
        innloggetBruker: Saksbehandler,
        skjermet: Boolean,
        brukerkontekst: BrukerkontekstMedOmråde,
    ): ReservasjonV3Dto {
        val tilSaksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(
            params.brukerIdent, skjermet
        ) ?: throw ManglerTilgangException("Fant ikke mottaker i valgt skjerming")

        val reservasjonsnøkkel = finnReservasjonsnøkkel(params.reservasjonsnøkkel, params.oppgaveNøkkel, brukerkontekst)
        reservasjonV3Tjeneste.kontrollerReservasjonstilgang(reservasjonsnøkkel, innloggetBruker, brukerkontekst, tilSaksbehandler)

        val nyReservasjon = reservasjonV3Tjeneste.overførReservasjon(
            reservasjonsnøkkel = reservasjonsnøkkel,
            reserverTil = LocalDateTime.now().leggTilDagerHoppOverHelg(1),
            tilSaksbehandlerId = tilSaksbehandler.id,
            utførtAvBrukerId = innloggetBruker.id,
            kommentar = params.begrunnelse,
        )
        log.info("overførReservasjon: ${nyReservasjon.reservasjonV3}, utførtAv: $innloggetBruker., tilSaksbehandler: $tilSaksbehandler")

        return reservasjonV3DtoBuilder.byggReservasjonV3Dto(nyReservasjon, tilSaksbehandler, brukerkontekst)
    }

    suspend fun annullerReservasjoner(
        params: List<AnnullerReservasjonDto>,
        innloggetBruker: Saksbehandler,
        brukerkontekst: BrukerkontekstMedOmråde,
    ) {
        val nøkler = params.map {
            finnReservasjonsnøkkel(it.reservasjonsnøkkel, it.oppgaveNøkkel, brukerkontekst).also { nøkkel ->
                reservasjonV3Tjeneste.kontrollerReservasjonstilgang(nøkkel, innloggetBruker, brukerkontekst, oppheving = true)
            }
        }
        nøkler.forEach {
            reservasjonV3Tjeneste.annullerReservasjonHvisFinnes(
                reservasjonsnøkkel = it,
                kommentar = null,
                annullertAvBrukerId = innloggetBruker.id,
            )
        }
    }

    private fun finnReservasjonsnøkkel(nøkkel: String?, oppgaveNøkkel: OppgaveNøkkelDto?, kontekst: BrukerkontekstMedOmråde): String {
        val fraOppgave = oppgaveNøkkel?.let {
            kontekst.krevOmråde(it.områdeEksternId)
            val oppgave = aktivOppgaveOppslag.hentAktivOppgave(it.oppgaveEksternId, it.oppgaveTypeEksternId, kontekst.område)
            kontekst.krevOmråde(oppgave.oppgavetype.område.tilOmrådeEnum())
            oppgave.reservasjonsnøkkel
        }
        require(nøkkel == null || fraOppgave == null || nøkkel == fraOppgave) { "Oppgave og reservasjonsnøkkel samsvarer ikke" }
        return requireNotNull(nøkkel ?: fraOppgave) { "Oppgave eller reservasjonsnøkkel må oppgis" }
    }

    private suspend fun filtrerReservasjoner(
        reservasjoner: List<ReservasjonV3MedOppgaver>, kontekst: BrukerkontekstMedOmråde,
        saksbehandler: Saksbehandler? = null,
    ): List<ReservasjonV3MedOppgaver> {
        if (!kontekst.harBasisTilgang) throw ManglerTilgangException("Mangler basistilgang")
        return reservasjoner.filter { it.reservasjonV3.område == kontekst.område }.mapNotNull { reservasjon ->
            // En egen reservasjon kan leve videre uten åpne oppgaver, f.eks. etter retur fra beslutter.
            // Dette er ikke det samme som at oppgavene er fjernet av tilgangsfilteret.
            if (reservasjon.oppgaverV3.isEmpty() && saksbehandler != null &&
                saksbehandler.navident == kontekst.navIdent && saksbehandler.id == reservasjon.reservasjonV3.reservertAv &&
                kontekst.område in saksbehandler.områder && saksbehandler.skjermet == kontekst.harTilgangTilKode6
            ) {
                return@mapNotNull reservasjon.copy(
                    reservasjonV3 = reservasjon.reservasjonV3.copy(
                        id = reservasjon.reservasjonV3.id, kommentar = null, endretAv = null,
                    )
                )
            }
            val oppgaver = reservasjon.oppgaverV3.filter {
                it.oppgavetype.område.eksternId == kontekst.område.eksternId &&
                    pepClient.harTilgangTilOppgaveV3(it, kontekst, Action.read)
            }
            if (oppgaver.isEmpty()) null else reservasjon.copy(oppgaverV3 = oppgaver)
        }
    }

    suspend fun hentReserverteOppgaverForSaksbehandler(saksbehandler: Saksbehandler, brukerkontekst: BrukerkontekstMedOmråde): List<ReservasjonV3Dto> {
        if (saksbehandler.skjermet != brukerkontekst.harTilgangTilKode6) return emptyList()
        val reservasjonerMedOppgaver =
            filtrerReservasjoner(reservasjonV3Tjeneste.hentReservasjonerForSaksbehandler(saksbehandler.id, brukerkontekst.område), brukerkontekst, saksbehandler)

        return reservasjonerMedOppgaver.map { reservasjonMedOppgaver ->
            try {
                reservasjonV3DtoBuilder.byggReservasjonV3Dto(reservasjonMedOppgaver, saksbehandler, brukerkontekst)
            } catch (e: Exception) {
                log.warn("Klarte ikke tolke reservasjon med id ${reservasjonMedOppgaver.reservasjonV3.id}, v3-oppgaver: ${reservasjonMedOppgaver.oppgaverV3.map { it.eksternId }}")
                throw e
            }
        }
    }

    suspend fun hentReserverteOppgaverSammendragForSaksbehandler(
        saksbehandler: Saksbehandler,
        brukerkontekst: BrukerkontekstMedOmråde,
    ): List<ReservasjonSammendragDto> {
        if (saksbehandler.skjermet != brukerkontekst.harTilgangTilKode6) return emptyList()
        val reservasjoner = filtrerReservasjoner(reservasjonV3Tjeneste.hentReservasjonerForSaksbehandler(saksbehandler.id, brukerkontekst.område), brukerkontekst, saksbehandler)
        // Bygger alle oppgavene i ett kall for å dele PDL-oppslag, og deler resultatet
        // tilbake per reservasjon. Forutsetter at builderen returnerer ett sammendrag
        // per oppgave i samme rekkefølge.
        val alleOppgaver = oppgaveSammendragDtoBuilder.bygg(reservasjoner.flatMap { it.oppgaverV3 }, brukerkontekst)
        var indeks = 0

        return reservasjoner.map { reservasjonMedOppgaver ->
            val oppgaver = alleOppgaver.subList(indeks, indeks + reservasjonMedOppgaver.oppgaverV3.size)
            indeks += reservasjonMedOppgaver.oppgaverV3.size

            val endretAvNavn = reservasjonMedOppgaver.reservasjonV3.endretAv?.let {
                saksbehandlerRepository.finnSaksbehandlerMedId(it)?.navn
            }
            ReservasjonSammendragDto(
                reservasjon = reservasjonMedOppgaver.reservasjonV3,
                oppgaver = oppgaver,
                reservertAv = saksbehandler,
                endretAvNavn = endretAvNavn,
            )
        }
    }

    suspend fun hentAktivReservasjon(oppgaveNøkkel: OppgaveNøkkelDto, brukerkontekst: BrukerkontekstMedOmråde): ReservasjonV3Dto? {
        if (!brukerkontekst.harBasisTilgang) throw ManglerTilgangException("Mangler basistilgang")
        brukerkontekst.krevOmråde(oppgaveNøkkel.områdeEksternId)
        val oppgave = aktivOppgaveOppslag.hentAktivOppgave(
                oppgaveNøkkel.oppgaveEksternId,
                oppgaveNøkkel.oppgaveTypeEksternId,
                brukerkontekst.område,
            )
        brukerkontekst.krevOmråde(oppgave.oppgavetype.område.tilOmrådeEnum())
        if (!pepClient.harTilgangTilOppgaveV3(
                oppgave = oppgave,
                brukerkontekst = brukerkontekst,
            )
        ) {
            throw ManglerTilgangException("Mangler tilgang til oppgave ${oppgave.eksternId}")
        }
        val reservasjon = reservasjonV3Tjeneste.finnAktivReservasjon(oppgave.reservasjonsnøkkel)
            ?: return null
        brukerkontekst.krevOmråde(reservasjon.område)
        val reservertAv = saksbehandlerRepository.finnSaksbehandlerMedId(reservasjon.reservertAv)
            ?: throw IllegalStateException("Fant ikke saksbehandler med id ${reservasjon.reservertAv} som har reservert oppgave ${oppgave.eksternId}")
        if (reservertAv.skjermet != brukerkontekst.harTilgangTilKode6) {
            throw ManglerTilgangException("Reservasjonen tilhører en annen skjerming")
        }
        return ReservasjonV3Dto(
            reservasjonV3 = reservasjon,
            oppgaver = emptyList(),
            reservertAv = reservertAv,
            endretAvNavn = null
        )
    }

    suspend fun hentAlleAktiveReservasjoner(kontekst: BrukerkontekstMedOmråde): List<ReservasjonDto> {
        if (!kontekst.erOppgavestyrer) throw ManglerTilgangException("Mangler tilgang til oppgavestyring")
        val innloggetBrukerHarKode6Tilgang = kontekst.harTilgangTilKode6

        return filtrerReservasjoner(reservasjonV3Tjeneste.hentAlleAktiveReservasjoner(kontekst.område), kontekst).flatMap { reservasjonMedOppgaver ->
            val saksbehandler =
                saksbehandlerRepository.finnSaksbehandlerMedId(reservasjonMedOppgaver.reservasjonV3.reservertAv)!!
            val saksbehandlerHarKode6Tilgang = saksbehandler.skjermet

            if (innloggetBrukerHarKode6Tilgang != saksbehandlerHarKode6Tilgang) {
                emptyList()
            } else {
                reservasjonMedOppgaver.oppgaverV3.map { oppgave ->
                    ReservasjonDto(
                        reservertAvEpost = saksbehandler.epost,
                        reservertAvIdent = saksbehandler.navident!!,
                        reservertAvId = saksbehandler.id,
                        reservertAvNavn = saksbehandler.navn,
                        saksnummer = oppgave.hentVerdi("saksnummer"), //TODO: Oppgaveagnostisk logikk. Løses antagelig ved å skrive om frontend i dette tilfellet
                        journalpostId = oppgave.hentVerdi("journalpostId"),
                        ytelse = oppgave.hentVerdi("ytelsestype")?.let { FagsakYtelseType.fraKode(it).navn }
                            ?: FagsakYtelseType.UKJENT.navn,
                        behandlingType = BehandlingType.fraKode(oppgave.hentVerdi("behandlingTypekode")!!),
                        reservertTilTidspunkt = reservasjonMedOppgaver.reservasjonV3.gyldigTil,
                        kommentar = reservasjonMedOppgaver.reservasjonV3.kommentar ?: "",
                        tilBeslutter = oppgave.hentVerdi("liggerHosBeslutter").toBoolean(),
                        oppgavenøkkel = OppgaveNøkkelDto(oppgave),
                        reservasjonsnøkkel = oppgave.reservasjonsnøkkel,
                    )
                }.toList()
            }
        }
    }
}
