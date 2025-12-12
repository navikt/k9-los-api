package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventLagret
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.klage.K9KlageEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.KlageEventTilOppgaveMapper
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.punsjtillos.PunsjEventTilOppgaveMapper
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.SakEventTilOppgaveMapper
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.tilbaketillos.TilbakeEventTilOppgaveMapper
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.NyOppgaveVersjonInnsending
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3

class EventTilOppgaveMapper(
    private val klageEventTilOppgaveMapper: KlageEventTilOppgaveMapper,
    private val punsjEventTilOppgaveMapper: PunsjEventTilOppgaveMapper,
    private val sakEventTilOppgaveMapper: SakEventTilOppgaveMapper,
    private val tilbakeEventTilOppgaveMapper: TilbakeEventTilOppgaveMapper,
) {
    internal fun mapOppgave(eventLagret: EventLagret, forrigeOppgaveversjon: OppgaveV3?, eventnummer: Int) : NyOppgaveVersjonInnsending {
        return when(eventLagret.fagsystem) {
            Fagsystem.K9SAK -> {
                sakEventTilOppgaveMapper.lagOppgaveDto(eventLagret, forrigeOppgaveversjon, eventnummer)
            }
            Fagsystem.K9TILBAKE -> {
                tilbakeEventTilOppgaveMapper.lagOppgaveDto(eventLagret, forrigeOppgaveversjon, eventnummer)
            }
            Fagsystem.K9KLAGE -> {
                klageEventTilOppgaveMapper.lagOppgaveDto(eventLagret, forrigeOppgaveversjon, eventnummer)
            }
            Fagsystem.PUNSJ -> {
                punsjEventTilOppgaveMapper.lagOppgaveDto(eventLagret, forrigeOppgaveversjon)
            }
        }
    }

    internal fun utledReservasjonsnøkkel(eventLagret: EventLagret, erTilBeslutter: Boolean) : String {
        return when(eventLagret.fagsystem) {
            Fagsystem.K9SAK -> {
                val event = LosObjectMapper.instance.readValue<K9SakEventDto>(eventLagret.eventJson)
                SakEventTilOppgaveMapper.utledReservasjonsnøkkel(event, erTilBeslutter)
            }
            Fagsystem.K9KLAGE -> {
                val event = LosObjectMapper.instance.readValue<K9KlageEventDto>(eventLagret.eventJson)
                KlageEventTilOppgaveMapper.utledReservasjonsnøkkel(event, erTilBeslutter)
            }
            Fagsystem.PUNSJ -> {
                val event = LosObjectMapper.instance.readValue<K9PunsjEventDto>(eventLagret.eventJson)
                PunsjEventTilOppgaveMapper.utledReservasjonsnøkkel(event)
            }
            Fagsystem.K9TILBAKE -> {
                val event = LosObjectMapper.instance.readValue<K9TilbakeEventDto>(eventLagret.eventJson)
                TilbakeEventTilOppgaveMapper.utledReservasjonsnøkkel(event, erTilBeslutter)
            }
        }
    }
}