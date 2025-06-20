package no.nav.k9.los.domene.lager.oppgave.v2

import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.modell.Aksjonspunkter
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import java.time.LocalDateTime
import java.util.*

fun Behandling.tilOppgaveV1(): Oppgave {
    val oppgave = oppgaver().last()

    return Oppgave(
        fagsakSaksnummer = eksternReferanse,
        journalpostId = eksternReferanse,
        aktorId = søkersId?.id ?: "",
        behandlendeEnhet = oppgave.ferdigstilt?.behandlendeEnhet ?: "",
        behandlingsfrist = oppgave.frist ?: LocalDateTime.MAX,
        behandlingOpprettet = opprettet,
        forsteStonadsdag = opprettet.toLocalDate(),
        behandlingStatus = BehandlingStatus.OPPRETTET,
        behandlingType = behandlingType?.let { BehandlingType.fraKode(it) } ?: BehandlingType.FORSTEGANGSSOKNAD,
        fagsakYtelseType = ytelseType,
        aktiv = oppgave.erAktiv(),
        system = fagsystem.kode,
        oppgaveAvsluttet = this.ferdigstilt,
        utfortFraAdmin = false,
        eksternId = UUID.fromString(eksternReferanse),
        oppgaveEgenskap = emptyList(),
        aksjonspunkter = Aksjonspunkter(mapOf(oppgave.oppgaveKode to "OPPR")),
        tilBeslutter = oppgave.erBeslutter,
        utbetalingTilBruker = false,
        selvstendigFrilans = false,
        kombinert = false,
        søktGradering = false,
        årskvantum = false,
        avklarArbeidsforhold = false,
        avklarMedlemskap = false,
        utenlands = false,
    )
}