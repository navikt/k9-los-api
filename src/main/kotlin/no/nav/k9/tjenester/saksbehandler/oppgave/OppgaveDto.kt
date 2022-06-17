package no.nav.k9.tjenester.saksbehandler.oppgave

import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.modell.BehandlingStatus
import no.nav.k9.domene.modell.BehandlingType
import no.nav.k9.domene.modell.FagsakYtelseType
import java.time.LocalDateTime
import java.util.*

class OppgaveDto(
    val status: OppgaveStatusDto,
    val behandlingId: Long?,
    val journalpostId: String?,
    val saksnummer: String?,
    val navn: String,
    val system: String,
    val personnummer: String,
    val behandlingstype: BehandlingType,
    val fagsakYtelseType: FagsakYtelseType,
    val behandlingStatus: BehandlingStatus,
    val erTilSaksbehandling: Boolean,
    val opprettetTidspunkt: LocalDateTime,
    val behandlingsfrist: LocalDateTime,
    val eksternId: UUID,
    val tilBeslutter: Boolean,
    val utbetalingTilBruker: Boolean,
    val avklarArbeidsforhold: Boolean,
    val selvstendigFrilans: Boolean,
    val søktGradering: Boolean,
    val fagsakPeriode: Oppgave.FagsakPeriode? = null,
    val paaVent: Boolean? = null,
    val harMerknad: Boolean = false
)
