package no.nav.k9.los.nyoppgavestyring.forvaltning

import no.nav.k9.klage.kodeverk.behandling.BehandlingType
import no.nav.k9.klage.kontrakt.behandling.oppgavetillos.Aksjonspunkttilstand
import no.nav.k9.klage.kontrakt.behandling.oppgavetillos.KlagebehandlingProsessHendelse
import no.nav.k9.klage.typer.Periode
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.klage.K9KlageEventDto
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class K9KlageEventIkkeSensitiv(

    val eksternId: String,
    val påklagdBehandlingEksternId: UUID?,
    val påklagdBehandlingType: BehandlingType?,
    val fagsystem: String,
    val behandlingstidFrist: LocalDate?,
    val saksnummer: String,
    val eventTid: LocalDateTime,
    val eventHendelse: String,
    val behandlingStatus: String,
    val behandlingSteg: String?,
    val behandlendeEnhet: String?,
    val ansvarligBeslutter: String?,
    val ansvarligSaksbehandler: String?,
    val resultatType: String?,
    val ytelseTypeKode: String,
    val behandlingTypeKode: String,
    val opprettetBehandling: LocalDateTime,
    val fagsakPeriode: Periode?,
    val aksjonspunkttilstander: List<Aksjonspunkttilstand>,
    val vedtaksdato: LocalDate?,
    val behandlingsårsaker: List<String>,
) {
    constructor(event: K9KlageEventDto) : this(
        eksternId = event.eksternId.toString(),
        påklagdBehandlingEksternId = event.påklagdBehandlingEksternId,
        påklagdBehandlingType = event.påklagdBehandlingType,
        fagsystem = event.fagsystem.toString(),
        behandlingstidFrist = event.behandlingstidFrist,
        saksnummer = event.saksnummer,
        eventTid = event.eventTid,
        eventHendelse = event.eventHendelse.toString(),
        behandlingStatus = event.behandlingStatus,
        behandlingSteg = event.behandlingSteg,
        behandlendeEnhet = event.behandlendeEnhet,
        ansvarligBeslutter = event.ansvarligBeslutter,
        ansvarligSaksbehandler = event.ansvarligSaksbehandler,
        resultatType = event.resultatType,
        ytelseTypeKode = event.ytelseTypeKode,
        behandlingTypeKode = event.behandlingTypeKode,
        opprettetBehandling = event.opprettetBehandling,
        fagsakPeriode = event.fagsakPeriode,
        aksjonspunkttilstander = event.aksjonspunkttilstander,
        vedtaksdato = event.vedtaksdato,
        behandlingsårsaker = event.behandlingsårsaker ?: emptyList(),
    )
}
