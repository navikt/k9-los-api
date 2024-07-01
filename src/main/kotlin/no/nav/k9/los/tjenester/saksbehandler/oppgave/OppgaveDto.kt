package no.nav.k9.los.tjenester.saksbehandler.oppgave

import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.modell.BehandlingStatus
import no.nav.k9.los.domene.modell.BehandlingType
import no.nav.k9.los.domene.modell.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import java.time.LocalDateTime
import java.util.*

class OppgaveDto(
    val status: OppgaveStatusDto,
    val behandlingId: Long?,  // bekreftet i bruk
    val journalpostId: String?, // bekreftet i bruk
    val saksnummer: String?, // bekreftet i bruk
    val navn: String, // bekreftet i bruk
    val system: String, // bekreftet i bruk
    val personnummer: String, // bekreftet i bruk
    val behandlingstype: BehandlingType, // bekreftet i bruk
    val fagsakYtelseType: FagsakYtelseType, // bekreftet i bruk
    val behandlingStatus: BehandlingStatus, // bekreftet i bruk
    val erTilSaksbehandling: Boolean, // bekreftet i bruk
    val opprettetTidspunkt: LocalDateTime, // bekreftet i bruk
    val behandlingsfrist: LocalDateTime, // bekreftet i bruk
    val eksternId: UUID, // bekreftet i bruk
    val tilBeslutter: Boolean,
    val utbetalingTilBruker: Boolean,
    val avklarArbeidsforhold: Boolean,
    val selvstendigFrilans: Boolean,
    val søktGradering: Boolean,
    val fagsakPeriode: Oppgave.FagsakPeriode? = null, // bekreftet i bruk
    val paaVent: Boolean? = null, // bekreftet i bruk
    val merknad: MerknadDto? = null, // bekreftet i bruk,
    val oppgaveNøkkel: OppgaveNøkkelDto = OppgaveNøkkelDto.forV1Oppgave(eksternId.toString()),
    val endretAvNavn: String? = null
)
