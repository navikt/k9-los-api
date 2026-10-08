package no.nav.k9.los.domeneadaptere.eventtiloppgave

import no.nav.k9.los.domeneadaptere.eventlager.EventLagret
import no.nav.k9.los.domeneadaptere.eventlager.Oppgavetype
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.klagetillos.KlageEventTilOppgaveMapper
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.kodeverk.K9Oppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.punsjtillos.PunsjEventTilOppgaveMapper
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.saktillos.SakEventTilOppgaveMapper
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.tilbaketillos.TilbakeEventTilOppgaveMapper
import no.nav.k9.los.infrastruktur.utils.IkkeImplementertException
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavemottak.NyOppgaveVersjonInnsending
import no.nav.k9.los.oppgavemottak.NyOppgaveversjon
import no.nav.k9.los.oppgavemottak.OppgaveDto
import no.nav.k9.los.oppgavemottak.OppgaveV3
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto
import no.nav.ung.kodeverk.behandling.BehandlingType
import no.nav.ung.kodeverk.behandling.BehandlingType.fraKode
import no.nav.ung.kodeverk.behandling.FagsakYtelseType
import java.time.LocalDateTime
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.mapper.SakEventTilOppgaveMapper as AktSakEventTilOppgaveMapper

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
        if (fraKode(this.eventDto.behandlingTypeKode) in listOf(BehandlingType.ANKE, BehandlingType.KLAGE)) {
            throw IkkeImplementertException("Ikke implementert ennå for anke/klage i UngSak")
        }
        when (FagsakYtelseType.fraKode(eventDto.ytelseTypeKode)) {
            FagsakYtelseType.AKTIVITETSPENGER ->
                AktSakEventTilOppgaveMapper.lagOppgaveDto(this, forrigeOppgaveversjon, eventnummer)

            FagsakYtelseType.UNGDOMSYTELSE -> throw IkkeImplementertException("Ikke implementert ennå")
            else -> throw IllegalStateException(ukjentUngSakYtelse)
        }
    }
}

internal fun EventLagret.oppgavetypeKode(): Oppgavetype = when (this) {
    is EventLagret.K9Sak -> K9Oppgavetypenavn.SAK
    is EventLagret.K9Tilbake -> K9Oppgavetypenavn.TILBAKE
    is EventLagret.K9Klage -> K9Oppgavetypenavn.KLAGE
    is EventLagret.K9Punsj -> K9Oppgavetypenavn.PUNSJ
    is EventLagret.UngSak -> when (FagsakYtelseType.fraKode(eventDto.ytelseTypeKode)) {
        FagsakYtelseType.AKTIVITETSPENGER -> AktSakEventTilOppgaveMapper.utledOppgavetype(eventDto)
        FagsakYtelseType.UNGDOMSYTELSE -> throw IkkeImplementertException("Ikke implementert ennå")
        else -> throw IllegalStateException(ukjentUngSakYtelse)
    }
}

internal fun EventLagret.oppgavestatus(): Oppgavestatus = when (this) {
    is EventLagret.K9Sak -> SakEventTilOppgaveMapper.utledOppgavestatus(eventDto)
    is EventLagret.K9Tilbake -> TilbakeEventTilOppgaveMapper.utledOppgavestatus(eventDto)
    is EventLagret.K9Klage -> KlageEventTilOppgaveMapper.utledOppgavestatus(eventDto)
    is EventLagret.K9Punsj -> PunsjEventTilOppgaveMapper.utledOppgavestatus(eventDto)
    is EventLagret.UngSak -> AktSakEventTilOppgaveMapper.utledOppgavestatus(eventDto)
}

internal fun EventLagret.eventTid(): LocalDateTime = when (this) {
    is EventLagret.K9Sak -> eventDto.eventTid
    is EventLagret.K9Tilbake -> eventDto.eventTid
    is EventLagret.K9Klage -> eventDto.eventTid
    is EventLagret.K9Punsj -> eventDto.eventTid
    is EventLagret.UngSak -> eventDto.eventTid
}

/**
 * Lukker [sisteOppgaveversjon] som følge av dette eventet. Feltverdiene videreføres uendret fra siste versjon,
 * bortsett fra felter med feltutleder, som utledes på nytt ved lagring.
 */
internal fun EventLagret.lukkOppgaveversjon(sisteOppgaveversjon: OppgaveV3): NyOppgaveVersjonInnsending {
    val utledesVedLagring = sisteOppgaveversjon.oppgavetype.oppgavefelter
        .filter { it.feltutleder != null }
        .map { it.feltDefinisjon.eksternId }
        .toSet()
    val forrige = OppgaveDto(sisteOppgaveversjon)
    return NyOppgaveversjon(
        forrige.copy(
            eksternVersjon = eksternVersjon,
            status = Oppgavestatus.LUKKET,
            endretTidspunkt = eventTid(),
            feltverdier = forrige.feltverdier.filterNot { it.nøkkel in utledesVedLagring },
        )
    )
}

internal fun EventLagret.oppgavenøkkel(): OppgaveNøkkelDto = OppgaveNøkkelDto(
    oppgaveEksternId = eksternId,
    oppgaveTypeEksternId = oppgavetypeKode().kode,
    områdeEksternId = område,
)

internal fun EventLagret.utledReservasjonsnøkkel(erTilBeslutter: Boolean): String = when (this) {
    is EventLagret.K9Sak -> SakEventTilOppgaveMapper.utledReservasjonsnøkkel(this, erTilBeslutter)
    is EventLagret.K9Klage -> KlageEventTilOppgaveMapper.utledReservasjonsnøkkel(this, erTilBeslutter)
    is EventLagret.K9Punsj -> PunsjEventTilOppgaveMapper.utledReservasjonsnøkkel(this)
    is EventLagret.K9Tilbake -> TilbakeEventTilOppgaveMapper.utledReservasjonsnøkkel(this, erTilBeslutter)
    is EventLagret.UngSak -> AktSakEventTilOppgaveMapper.utledReservasjonsnokkel(this, erTilBeslutter)
}

private const val ukjentUngSakYtelse =
    "UngSak-eventer skal aldri ha andre ytelsestyper enn aktivitetspenger eller ungdomsprogramytelsen"

