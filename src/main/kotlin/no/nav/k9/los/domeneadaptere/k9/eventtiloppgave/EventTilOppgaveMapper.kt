package no.nav.k9.los.domeneadaptere.k9.eventtiloppgave

import no.nav.k9.los.domeneadaptere.eventlager.EventLagret
import no.nav.k9.los.domeneadaptere.k9.K9Oppgavetypenavn
import no.nav.k9.los.domeneadaptere.k9.eventtiloppgave.klagetillos.KlageEventTilOppgaveMapper
import no.nav.k9.los.domeneadaptere.k9.eventtiloppgave.punsjtillos.PunsjEventTilOppgaveMapper
import no.nav.k9.los.domeneadaptere.k9.eventtiloppgave.saktillos.SakEventTilOppgaveMapper
import no.nav.k9.los.domeneadaptere.k9.eventtiloppgave.tilbaketillos.TilbakeEventTilOppgaveMapper
import no.nav.k9.los.oppgavemottak.NyOppgaveVersjonInnsending
import no.nav.k9.los.oppgavemottak.OppgaveV3

class EventTilOppgaveMapper(
    private val klageEventTilOppgaveMapper: KlageEventTilOppgaveMapper,
    private val punsjEventTilOppgaveMapper: PunsjEventTilOppgaveMapper,
    private val sakEventTilOppgaveMapper: SakEventTilOppgaveMapper,
    private val tilbakeEventTilOppgaveMapper: TilbakeEventTilOppgaveMapper,
) {
    internal fun mapOppgave(eventLagret: EventLagret, forrigeOppgaveversjon: OppgaveV3?, eventnummer: Int) : NyOppgaveVersjonInnsending {
        return when(eventLagret) {
            is EventLagret.K9Sak -> sakEventTilOppgaveMapper.lagOppgaveDto(eventLagret, forrigeOppgaveversjon, eventnummer)
            is EventLagret.K9Tilbake -> tilbakeEventTilOppgaveMapper.lagOppgaveDto(eventLagret, forrigeOppgaveversjon, eventnummer)
            is EventLagret.K9Klage -> klageEventTilOppgaveMapper.lagOppgaveDto(eventLagret, forrigeOppgaveversjon, eventnummer)
            is EventLagret.K9Punsj -> punsjEventTilOppgaveMapper.lagOppgaveDto(eventLagret, forrigeOppgaveversjon)
            is EventLagret.UngSak -> throw UnsupportedOperationException("UngSak-eventer skal ikke behandles av K9-pipeline")
        }
    }

    internal fun oppgavetypeKode(eventLagret: EventLagret): String = when (eventLagret) {
        is EventLagret.K9Sak     -> K9Oppgavetypenavn.SAK.kode
        is EventLagret.K9Tilbake -> K9Oppgavetypenavn.TILBAKE.kode
        is EventLagret.K9Klage   -> K9Oppgavetypenavn.KLAGE.kode
        is EventLagret.K9Punsj   -> K9Oppgavetypenavn.PUNSJ.kode
        is EventLagret.UngSak    -> throw UnsupportedOperationException(
            "UngSak-eventer skal ikke behandles av K9-pipeline"
        )
    }

    internal fun utledReservasjonsnøkkel(eventLagret: EventLagret, erTilBeslutter: Boolean): String {
        return when (eventLagret) {
            is EventLagret.K9Sak -> SakEventTilOppgaveMapper.utledReservasjonsnøkkel(eventLagret, erTilBeslutter)
            is EventLagret.K9Klage -> KlageEventTilOppgaveMapper.utledReservasjonsnøkkel(eventLagret, erTilBeslutter)
            is EventLagret.K9Punsj -> PunsjEventTilOppgaveMapper.utledReservasjonsnøkkel(eventLagret)
            is EventLagret.K9Tilbake -> TilbakeEventTilOppgaveMapper.utledReservasjonsnøkkel(eventLagret, erTilBeslutter)
            is EventLagret.UngSak -> throw UnsupportedOperationException("UngSak-eventer skal ikke behandles av K9-pipeline")
        }
    }
}