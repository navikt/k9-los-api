package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventLagret
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.AksjonspunktDefinisjonK9Tilbake
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.cache.PepCacheService
import no.nav.k9.los.nyoppgavestyring.ko.KøpåvirkendeHendelse
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveHendelseMottatt
import no.nav.k9.los.nyoppgavestyring.kodeverk.AksjonspunktStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.db.EksternOppgaveId
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class OppgaveOppdatertHandler(
    private val oppgaveRepository: OppgaveRepository,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste,
    private val eventTilOppgaveMapper: EventTilOppgaveMapper,
    private val pepCacheService: PepCacheService,
    private val køpåvirkendeHendelseChannel: Channel<KøpåvirkendeHendelse>,
) {
    private val log: Logger = LoggerFactory.getLogger(OppgaveOppdatertHandler::class.java)

    internal fun oppdaterPepCache(oppgave: OppgaveV3, tx: TransactionalSession) {
        pepCacheService.oppdater(tx, oppgave.kildeområde, oppgave.eksternId)
    }

    internal fun håndterOppgaveOppdatert(
        eventLagret: EventLagret,
        oppgave: OppgaveV3,
        tx: TransactionalSession,
    ) {
        runBlocking {
            køpåvirkendeHendelseChannel.send(
                OppgaveHendelseMottatt(
                    eventLagret.fagsystem,
                    EksternOppgaveId("K9", oppgave.eksternId)
                )
            )
        }

        when (eventLagret) {
            is EventLagret.K9Sak -> håndterSakOppdatert(eventLagret, oppgave, tx)
            is EventLagret.K9Tilbake -> håndterTilbakeOppdatert(eventLagret, oppgave, tx)
            is EventLagret.K9Klage -> håndterKlageOppdatert(eventLagret, oppgave, tx)
            is EventLagret.K9Punsj -> håndterPunsjOppdatert(oppgave, tx)
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

    private fun håndterKlageOppdatert(eventLagret: EventLagret.K9Klage, oppgave: OppgaveV3, tx: TransactionalSession) {
        val event = eventLagret.eventDto
        val erPåVent = event.aksjonspunkttilstander.any {
            it.status.erÅpentAksjonspunkt()
                    && no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon.fraKode(it.aksjonspunktKode)
                .erAutopunkt()
        }
        if (erPåVent || BehandlingStatus.AVSLUTTET.kode == event.behandlingStatus || oppgave.status == Oppgavestatus.LUKKET) {
            annullerReservasjonerHvisAlleOppgaverPåVentEllerAvsluttet(eventLagret, oppgave, tx)
        }
    }

    private fun håndterTilbakeOppdatert(
        eventLagret: EventLagret.K9Tilbake,
        oppgave: OppgaveV3,
        tx: TransactionalSession
    ) {
        val event = eventLagret.eventDto
        val erPåVent = event.aksjonspunktKoderMedStatusListe.any {
            it.value == AksjonspunktStatus.OPPRETTET.kode && AksjonspunktDefinisjonK9Tilbake.fraKode(it.key).erAutopunkt
        }
        if (erPåVent || BehandlingStatus.AVSLUTTET.kode == event.behandlingStatus || oppgave.status == Oppgavestatus.LUKKET) {
            annullerReservasjonerHvisAlleOppgaverPåVentEllerAvsluttet(eventLagret, oppgave, tx)
        }
    }

    private fun håndterSakOppdatert(eventLagret: EventLagret.K9Sak, oppgave: OppgaveV3, tx: TransactionalSession) {
        val event = eventLagret.eventDto
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