package no.nav.k9.los.domeneadaptere.eventtiloppgave

import no.nav.k9.los.domeneadaptere.eventlager.EventLagret
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.mapper.SakEventTilOppgaveMapper as AktSakEventTilOppgaveMapper
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.klagetillos.KlageEventTilOppgaveMapper
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.kodeverk.K9Oppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.punsjtillos.PunsjEventTilOppgaveMapper
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.saktillos.SakEventTilOppgaveMapper
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.tilbaketillos.TilbakeEventTilOppgaveMapper
import no.nav.k9.los.infrastruktur.utils.IkkeImplementertException
import no.nav.k9.los.oppgavemottak.NyOppgaveVersjonInnsending
import no.nav.k9.los.oppgavemottak.OppgaveV3
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto
import no.nav.ung.kodeverk.behandling.BehandlingType
import no.nav.ung.kodeverk.behandling.FagsakYtelseType

/**
 * Ruting fra lagret event til riktig mapper. Mapperne er rene funksjoner – eventuelle
 * oppslag mot kildesystemene er gjort på forhånd av [EventBeriker].
 */
internal fun EventLagret.tilOppgaveversjon(
    forrigeOppgaveversjon: OppgaveV3?,
    eventnummer: Int,
): NyOppgaveVersjonInnsending = when (this) {
    is EventLagret.K9Sak -> SakEventTilOppgaveMapper.lagOppgaveDto(this, forrigeOppgaveversjon, eventnummer)
    is EventLagret.K9Tilbake -> TilbakeEventTilOppgaveMapper.lagOppgaveDto(this, forrigeOppgaveversjon, eventnummer)
    is EventLagret.K9Klage -> KlageEventTilOppgaveMapper.lagOppgaveDto(this, forrigeOppgaveversjon, eventnummer)
    is EventLagret.K9Punsj -> PunsjEventTilOppgaveMapper.lagOppgaveDto(this, forrigeOppgaveversjon)
    is EventLagret.UngSak -> {
        krevStøttetUngSakBehandlingstype(this)
        when (FagsakYtelseType.fraKode(eventDto.ytelseTypeKode)) {
            FagsakYtelseType.AKTIVITETSPENGER ->
                AktSakEventTilOppgaveMapper.lagOppgaveDto(this, forrigeOppgaveversjon, eventnummer)

            FagsakYtelseType.UNGDOMSYTELSE -> throw IkkeImplementertException("Ikke implementert ennå")
            else -> throw IllegalStateException(ukjentUngSakYtelse)
        }
    }
}

internal fun EventLagret.oppgavetypeKode(): String = when (this) {
    is EventLagret.K9Sak -> K9Oppgavetypenavn.SAK.kode
    is EventLagret.K9Tilbake -> K9Oppgavetypenavn.TILBAKE.kode
    is EventLagret.K9Klage -> K9Oppgavetypenavn.KLAGE.kode
    is EventLagret.K9Punsj -> K9Oppgavetypenavn.PUNSJ.kode
    is EventLagret.UngSak -> when (FagsakYtelseType.fraKode(eventDto.ytelseTypeKode)) {
        FagsakYtelseType.AKTIVITETSPENGER -> AktSakEventTilOppgaveMapper.utledOppgavetype(eventDto).kode
        FagsakYtelseType.UNGDOMSYTELSE -> throw IkkeImplementertException("Ikke implementert ennå")
        else -> throw IllegalStateException(ukjentUngSakYtelse)
    }
}

internal fun EventLagret.oppgavenøkkel(): OppgaveNøkkelDto = OppgaveNøkkelDto(
    oppgaveEksternId = eksternId,
    oppgaveTypeEksternId = oppgavetypeKode(),
    områdeEksternId = område,
)

internal fun EventLagret.utledReservasjonsnøkkel(erTilBeslutter: Boolean): String = when (this) {
    is EventLagret.K9Sak -> SakEventTilOppgaveMapper.utledReservasjonsnøkkel(this, erTilBeslutter)
    is EventLagret.K9Klage -> KlageEventTilOppgaveMapper.utledReservasjonsnøkkel(this, erTilBeslutter)
    is EventLagret.K9Punsj -> PunsjEventTilOppgaveMapper.utledReservasjonsnøkkel(this)
    is EventLagret.K9Tilbake -> TilbakeEventTilOppgaveMapper.utledReservasjonsnøkkel(this, erTilBeslutter)
    is EventLagret.UngSak -> AktSakEventTilOppgaveMapper.utledReservasjonsnokkel(this, erTilBeslutter)
}

private fun krevStøttetUngSakBehandlingstype(eventLagret: EventLagret.UngSak) {
    if (BehandlingType.fraKode(eventLagret.eventDto.behandlingTypeKode) in listOf(BehandlingType.ANKE, BehandlingType.KLAGE)) {
        throw IkkeImplementertException("Ikke implementert ennå for anke/klage i UngSak")
    }
}

private const val ukjentUngSakYtelse =
    "UngSak-eventer skal aldri ha andre ytelsestyper enn aktivitetspenger eller ungdomsprogramytelsen"

