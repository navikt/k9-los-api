package no.nav.k9.domene.lager.oppgave.v2

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.modell.Fagsystem
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

internal class BehandlingTest {

    val oppgave1 = OpprettOppgave(tidspunkt = LocalDateTime.now().minusHours(12), oppgaveKode = "OPPGAVE1", frist = null)
    val oppgave2 = OpprettOppgave(tidspunkt = LocalDateTime.now().minusHours(1), oppgaveKode = "OPPGAVE2", frist = null)

    @Test
    fun `lukkAktiveOppgaverFørOppgittOppgavekode ferdigstiller ikke etterfolgende oppgaver ved oppgitt OppgaveKode`() {
        val behandling = opprettBehandling()

        behandling.nyOppgave(oppgave1)
        behandling.nyOppgave(oppgave2)

        behandling.lukkAktiveOppgaverFørOppgittOppgavekode(
            FerdigstillOppgave(
                tidspunkt = LocalDateTime.MAX,
                oppgaveKode = oppgave1.oppgaveKode
            )
        )
        assertThat(behandling.oppgaver().filter { it.erAktiv() }.map { it.oppgaveKode }).containsExactly(oppgave2.oppgaveKode)
    }

    @Test
    fun `lukkAktiveOppgaverFørOppgittOppgavekode ferdigstiller foregående oppgaver ved oppgitt OppgaveKode`() {
        val behandling = opprettBehandling()
        behandling.nyOppgave(oppgave1)
        behandling.nyOppgave(oppgave2)

        behandling.lukkAktiveOppgaverFørOppgittOppgavekode(
            FerdigstillOppgave(
                tidspunkt = LocalDateTime.MAX,
                oppgaveKode = oppgave2.oppgaveKode
            )
        )
        assertThat(behandling.harAktiveOppgaver()).isFalse()
    }

    @Test
    fun `lukkAktiveOppgaverFørOppgittOppgavekode ferdigstiller alle oppgaver ved null OppgaveKode`() {
        val behandling = opprettBehandling()
        behandling.nyOppgave(oppgave1)
        behandling.nyOppgave(oppgave2)

        behandling.lukkAktiveOppgaverFørOppgittOppgavekode(
            FerdigstillOppgave(
                tidspunkt = LocalDateTime.MAX,
                oppgaveKode = null
            )
        )
        assertThat(behandling.oppgaver().filter { it.erAktiv() }).isEmpty()
    }

    @Test
    fun `ferdigstill lukker alle oppgaver og setter ferdigstilt-tidspunkt på behandling`() {
        val behandling = opprettBehandling()

        behandling.nyOppgave(oppgave1)
        val ferdigstiltTidspunkt = LocalDateTime.now()
        behandling.ferdigstill(FerdigstillBehandling(tidspunkt = ferdigstiltTidspunkt))

        assertThat(behandling.oppgaver().filter { it.erAktiv() }).isEmpty()
        assertThat(behandling.ferdigstilt).isEqualTo(ferdigstiltTidspunkt)
    }

    private fun opprettBehandling() = Behandling.ny(
        eksternReferanse = UUID.randomUUID().toString(),
        fagsystem = Fagsystem.K9SAK,
        ytelseType = FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
        behandlingType = " ",
        søkersId = Ident("123456", Ident.IdType.AKTØRID)
    )
}