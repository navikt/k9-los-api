package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave

import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventLagret
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.KlageEventTilOppgaveMapper
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.punsjtillos.PunsjEventTilOppgaveMapper
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.SakEventTilOppgaveMapper
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.tilbaketillos.TilbakeEventTilOppgaveMapper
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.NyOppgaveVersjonInnsending
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3

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
        }
    }

    internal fun utledReservasjonsnøkkel(eventLagret: EventLagret, erTilBeslutter: Boolean): String {
        return when (eventLagret) {
            is EventLagret.K9Sak -> SakEventTilOppgaveMapper.utledReservasjonsnøkkel(eventLagret, erTilBeslutter)
            is EventLagret.K9Klage -> KlageEventTilOppgaveMapper.utledReservasjonsnøkkel(eventLagret, erTilBeslutter)
            is EventLagret.K9Punsj -> PunsjEventTilOppgaveMapper.utledReservasjonsnøkkel(eventLagret)
            is EventLagret.K9Tilbake -> TilbakeEventTilOppgaveMapper.utledReservasjonsnøkkel(eventLagret, erTilBeslutter)
        }
    }
}