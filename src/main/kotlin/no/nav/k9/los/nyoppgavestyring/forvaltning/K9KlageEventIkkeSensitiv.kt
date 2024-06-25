package no.nav.k9.los.nyoppgavestyring.forvaltning

import no.nav.k9.klage.kontrakt.behandling.oppgavetillos.Aksjonspunkttilstand
import no.nav.k9.klage.kontrakt.behandling.oppgavetillos.KlagebehandlingProsessHendelse
import no.nav.k9.klage.typer.Periode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class K9KlageEventIkkeSensitiv(

    private val eksternId: String,
    private val påklagdBehandlingEksternId: UUID?,
    private val fagsystem: String,
    private val behandlingstidFrist: LocalDate?,
    private val saksnummer: String,
    private val eventTid: LocalDateTime,
    private val eventHendelse: String,
    private val behandlingStatus: String,
    private val behandlingSteg: String?,
    private val behandlendeEnhet: String?,
    private val ansvarligBeslutter: String,
    private val ansvarligSaksbehandler: String?,
    private val resultatType: String?,
    private val ytelseTypeKode: String,
    private val behandlingTypeKode: String,
    private val opprettetBehandling: LocalDateTime,
    private val fagsakPeriode: Periode?,
    private val aksjonspunkttilstander: List<Aksjonspunkttilstand>,
    private val vedtaksdato: LocalDate?,
    private val behandlingsårsaker: List<String>,
) {
    constructor(event: KlagebehandlingProsessHendelse) : this(
        eksternId = event.eksternId.toString(),
        påklagdBehandlingEksternId = event.påklagdBehandlingEksternId,
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
        behandlingsårsaker = event.behandlingsårsaker,
    )
}
