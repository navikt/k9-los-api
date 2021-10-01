package no.nav.k9.domene.modell

import no.nav.k9.domene.lager.oppgave.Oppgave
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AksjonspunktTest {


    @Test
    fun `Skal takle utgått aksjonspunkt`() {
        val oppgave = Oppgave(
            behandlingId = 9438,
            fagsakSaksnummer = "",
            aktorId = "273857",
            journalpostId = "234234535",
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = LocalDateTime.now().minusDays(23),
            forsteStonadsdag = LocalDate.now().plusDays(6),
            behandlingStatus = BehandlingStatus.OPPRETTET,
            behandlingType = BehandlingType.UKJENT,
            fagsakYtelseType = FagsakYtelseType.UKJENT,
            aktiv = true,
            system = "PUNSJ",
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(mapOf("5002" to "OPPR")),
            tilBeslutter = false,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false
        )


        val harAktivtAutopunkt = AksjonspunktDefWrapper.harAktivtAutopunkt(oppgave)
        assertFalse(harAktivtAutopunkt)
    }

    @Test
    fun `Skal få true på auto aksjonspunkt`() {
        val oppgave = Oppgave(
            behandlingId = 9438,
            fagsakSaksnummer = "",
            aktorId = "273857",
            journalpostId = "234234535",
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = LocalDateTime.now().minusDays(23),
            forsteStonadsdag = LocalDate.now().plusDays(6),
            behandlingStatus = BehandlingStatus.OPPRETTET,
            behandlingType = BehandlingType.UKJENT,
            fagsakYtelseType = FagsakYtelseType.UKJENT,
            aktiv = true,
            system = "PUNSJ",
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(mapOf("7030" to "OPPR")),
            tilBeslutter = false,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false
        )


        val harAktivtAutopunkt = AksjonspunktDefWrapper.harAktivtAutopunkt(oppgave)
        assertTrue(harAktivtAutopunkt)
    }
}
