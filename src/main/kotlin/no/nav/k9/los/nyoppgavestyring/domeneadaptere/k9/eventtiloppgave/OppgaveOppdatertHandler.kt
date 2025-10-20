package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.TransactionalSession
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventLagret
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.klage.K9KlageEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.AksjonspunktDefinisjonK9Tilbake
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeEventDto
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.cache.PepCacheService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.kodeverk.AksjonspunktStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class OppgaveOppdatertHandler(
    private val oppgaveRepository: OppgaveRepository,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste,
    private val eventTilOppgaveMapper: EventTilOppgaveMapper,
    private val pepCacheService: PepCacheService
) {
    private val log: Logger = LoggerFactory.getLogger(OppgaveOppdatertHandler::class.java)

    internal fun håndterOppgaveOppdatert(eventLagret: EventLagret, oppgave: OppgaveV3, tx: TransactionalSession) {
        pepCacheService.oppdater(tx, oppgave.kildeområde, oppgave.eksternId)
        when (eventLagret.fagsystem) {
            Fagsystem.K9SAK -> {
                håndterSakOppdatert(eventLagret, oppgave, tx)
            }
            Fagsystem.K9TILBAKE -> {
                håndterTilbakeOppdatert(eventLagret, oppgave, tx)
            }
            Fagsystem.K9KLAGE -> {
                håndterKlageOppdatert(eventLagret, oppgave, tx)
            }
            Fagsystem.PUNSJ -> {
                håndterPunsjOppdatert(oppgave, tx)
            }
        }
    }

    private fun håndterPunsjOppdatert(
        oppgave: OppgaveV3,
        tx: TransactionalSession
    ) {
        if (oppgave.status == Oppgavestatus.LUKKET || oppgave.status == Oppgavestatus.VENTER) {
            reservasjonV3Tjeneste.annullerReservasjonHvisFinnes(
                oppgave.reservasjonsnøkkel,
                "Maskinelt annullert reservasjon, siden oppgave på reservasjonen er avsluttet eller på vent",
                null,
                tx
            )
        }
    }

    private fun håndterKlageOppdatert(eventLagret: EventLagret, oppgave: OppgaveV3, tx: TransactionalSession) {
        val event = LosObjectMapper.instance.readValue<K9KlageEventDto>(eventLagret.eventJson)
        val erPåVent  = event.aksjonspunkttilstander.any {
            it.status.erÅpentAksjonspunkt()
                    && no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon.fraKode(it.aksjonspunktKode).erAutopunkt()
        }
        if (erPåVent || BehandlingStatus.AVSLUTTET.kode == event.behandlingStatus || oppgave.status == Oppgavestatus.LUKKET) {
            annullerReservasjonerHvisAlleOppgaverPåVentEllerAvsluttet(eventLagret, oppgave, tx)
        }
    }

    private fun håndterTilbakeOppdatert(eventLagret: EventLagret, oppgave: OppgaveV3, tx: TransactionalSession) {
        val event = LosObjectMapper.instance.readValue<K9TilbakeEventDto>(eventLagret.eventJson)
        val erPåVent = event.aksjonspunktKoderMedStatusListe.any { it.value == AksjonspunktStatus.OPPRETTET.kode && AksjonspunktDefinisjonK9Tilbake.fraKode(it.key).erAutopunkt }
        if (erPåVent || BehandlingStatus.AVSLUTTET.kode == event.behandlingStatus || oppgave.status == Oppgavestatus.LUKKET) {
            annullerReservasjonerHvisAlleOppgaverPåVentEllerAvsluttet(eventLagret, oppgave, tx)
        }
    }

    private fun håndterSakOppdatert(eventLagret: EventLagret, oppgave: OppgaveV3, tx: TransactionalSession) {
        val event = LosObjectMapper.instance.readValue<K9SakEventDto>(eventLagret.eventJson)
        val erPåVent = event.aksjonspunktTilstander.any {
            it.status.erÅpentAksjonspunkt() && AksjonspunktDefinisjon.fraKode(it.aksjonspunktKode)
                .erAutopunkt()
        }
        if (erPåVent || BehandlingStatus.AVSLUTTET.kode == event.behandlingStatus || oppgave.status == Oppgavestatus.LUKKET) {
            annullerReservasjonerHvisAlleOppgaverPåVentEllerAvsluttet(eventLagret, oppgave, tx)
        }
    }

    private fun annullerReservasjonerHvisAlleOppgaverPåVentEllerAvsluttet(
        event: EventLagret,
        oppgave: OppgaveV3,
        tx: TransactionalSession
    ) {
        val saksbehandlerNøkkel = eventTilOppgaveMapper.utledReservasjonsnøkkel(event, erTilBeslutter = false)
        val beslutterNøkkel = eventTilOppgaveMapper.utledReservasjonsnøkkel(event, erTilBeslutter = true)
        val antallAnnullert =
            annullerReservasjonHvisAlleOppgaverPåVentEllerAvsluttet(listOf(saksbehandlerNøkkel, beslutterNøkkel), tx)
        if (antallAnnullert > 0) {
            log.info("Annullerte $antallAnnullert reservasjoner maskinelt på oppgave ${oppgave.hentVerdi("saksnummer")} som følge av status på innkommende event")
        } else {
            log.info("Annullerte ingen reservasjoner på oppgave ${oppgave.hentVerdi("saksnummer")} som følge av status på innkommende event")
        }
    }

    private fun annullerReservasjonHvisAlleOppgaverPåVentEllerAvsluttet(
        reservasjonsnøkler: List<String>,
        tx: TransactionalSession
    ): Int {
        //TODO:
        //hent reservasjon for nøkler
        //hvis opprettet dato for reservasjon er før eventLagret.eventTid
        //  -- utfør resten av denne funksjonen for den aktuelle reservasjonen
        //hvis ikke
        //  -- return
        val åpneOppgaverForReservasjonsnøkkel =
            oppgaveRepository.hentAlleÅpneOppgaverForReservasjonsnøkkel(tx, reservasjonsnøkler)
                .filter { it.status == Oppgavestatus.AAPEN.kode }

        if (åpneOppgaverForReservasjonsnøkkel.isEmpty()) {
            return reservasjonsnøkler.map { reservasjonsnøkkel ->
                reservasjonV3Tjeneste.annullerReservasjonHvisFinnes(
                    reservasjonsnøkkel,
                    "Maskinelt annullert reservasjon, siden alle oppgaver på reservasjonen står på vent eller er avsluttet",
                    null,
                    tx
                )
            }.count { it }
        }
        log.info("Oppgave annulleres ikke fordi det finnes andre åpne oppgaver: [${åpneOppgaverForReservasjonsnøkkel.map { it.eksternId }}]")
        return 0
    }
}