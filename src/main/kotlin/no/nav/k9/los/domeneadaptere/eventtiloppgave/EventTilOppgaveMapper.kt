package no.nav.k9.los.domeneadaptere.eventtiloppgave

import no.nav.k9.los.domeneadaptere.eventlager.EventLagret
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.klagetillos.KlageEventTilOppgaveMapper
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.kodeverk.K9Oppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.punsjtillos.PunsjEventTilOppgaveMapper
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.saktillos.SakEventTilOppgaveMapper
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.tilbaketillos.TilbakeEventTilOppgaveMapper
import no.nav.k9.los.infrastruktur.utils.IkkeImplementertException
import no.nav.k9.los.oppgavemottak.NyOppgaveVersjonInnsending
import no.nav.k9.los.oppgavemottak.OppgaveV3
import no.nav.ung.kodeverk.behandling.BehandlingType
import no.nav.ung.kodeverk.behandling.FagsakYtelseType

class EventTilOppgaveMapper(
    private val klageEventTilOppgaveMapper: KlageEventTilOppgaveMapper,
    private val punsjEventTilOppgaveMapper: PunsjEventTilOppgaveMapper,
    private val sakEventTilOppgaveMapper: SakEventTilOppgaveMapper,
    private val tilbakeEventTilOppgaveMapper: TilbakeEventTilOppgaveMapper
) {
    internal fun mapOppgave(eventLagret: EventLagret, forrigeOppgaveversjon: OppgaveV3?, eventnummer: Int) : NyOppgaveVersjonInnsending {
        return when(eventLagret) {
            is EventLagret.K9Sak -> sakEventTilOppgaveMapper.lagOppgaveDto(eventLagret, forrigeOppgaveversjon, eventnummer)
            is EventLagret.K9Tilbake -> tilbakeEventTilOppgaveMapper.lagOppgaveDto(eventLagret, forrigeOppgaveversjon, eventnummer)
            is EventLagret.K9Klage -> klageEventTilOppgaveMapper.lagOppgaveDto(eventLagret, forrigeOppgaveversjon, eventnummer)
            is EventLagret.K9Punsj -> punsjEventTilOppgaveMapper.lagOppgaveDto(eventLagret, forrigeOppgaveversjon)
            is EventLagret.UngSak -> {
                if (BehandlingType.fraKode(eventLagret.eventDto.behandlingTypeKode) in listOf(BehandlingType.ANKE, BehandlingType.KLAGE)) {
                    throw IkkeImplementertException("Ikke implementert ennå for anke/klage i UngSak")
                }
                when (FagsakYtelseType.fraKode(eventLagret.eventDto.ytelseTypeKode)) {
                    FagsakYtelseType.AKTIVITETSPENGER -> no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.mapper.SakEventTilOppgaveMapper.lagOppgaveDto(eventLagret, forrigeOppgaveversjon, eventnummer)
                    FagsakYtelseType.UNGDOMSYTELSE -> throw IkkeImplementertException("Ikke implementert ennå")
                    else -> throw IllegalStateException("UngSak-eventer skal aldri ha andre ytelsestyper enn aktivitetspenger eller ungdomsprogramytelsen")
                }
                throw UnsupportedOperationException("UngSak-eventer skal ikke behandles av K9-pipeline")
            }
        }
    }

    internal fun oppgavetypeKode(eventLagret: EventLagret): String {
        return when (eventLagret) {
            is EventLagret.K9Sak     -> K9Oppgavetypenavn.SAK.kode
            is EventLagret.K9Tilbake -> K9Oppgavetypenavn.TILBAKE.kode
            is EventLagret.K9Klage   -> K9Oppgavetypenavn.KLAGE.kode
            is EventLagret.K9Punsj   -> K9Oppgavetypenavn.PUNSJ.kode
            is EventLagret.UngSak    ->
                when (FagsakYtelseType.fraKode(eventLagret.eventDto.ytelseTypeKode)) {
                    FagsakYtelseType.AKTIVITETSPENGER -> no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.mapper.SakEventTilOppgaveMapper.utledOppgavetype(eventLagret.eventDto).kode
                    FagsakYtelseType.UNGDOMSYTELSE -> throw IkkeImplementertException("Ikke implementert ennå")
                    else -> throw IllegalStateException("UngSak-eventer skal aldri ha andre ytelsestyper enn aktivitetspenger eller ungdomsprogramytelsen")
            }
        }
    }

    internal fun utledReservasjonsnøkkel(eventLagret: EventLagret, erTilBeslutter: Boolean): String {
        return when (eventLagret) {
            is EventLagret.K9Sak -> SakEventTilOppgaveMapper.utledReservasjonsnøkkel(eventLagret, erTilBeslutter)
            is EventLagret.K9Klage -> KlageEventTilOppgaveMapper.utledReservasjonsnøkkel(eventLagret, erTilBeslutter)
            is EventLagret.K9Punsj -> PunsjEventTilOppgaveMapper.utledReservasjonsnøkkel(eventLagret)
            is EventLagret.K9Tilbake -> TilbakeEventTilOppgaveMapper.utledReservasjonsnøkkel(eventLagret, erTilBeslutter)
            is EventLagret.UngSak -> no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.mapper.SakEventTilOppgaveMapper.utledReservasjonsnokkel(eventLagret, erTilBeslutter)
        }
    }
}