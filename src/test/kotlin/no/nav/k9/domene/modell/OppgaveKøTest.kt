package no.nav.k9.domene.modell

import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.tjenester.avdelingsleder.oppgaveko.AndreKriterierDto
import org.junit.Assert
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*


internal class OppgaveKøTest {
    @Test
    fun `Punsj oppgave skal med i køen med dette filter oppsettet`() {
        val oppgaveKø = OppgaveKø(
            UUID.randomUUID(),
            "test",
            LocalDate.now(),
            KøSortering.OPPRETT_BEHANDLING,
            mutableListOf(),
            mutableListOf(
                FagsakYtelseType.OMSORGSPENGER,
                FagsakYtelseType.OMSORGSDAGER,
                FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
                FagsakYtelseType.UKJENT
            ),
            mutableListOf(AndreKriterierDto("1", AndreKriterierType.FRA_PUNSJ, checked = true, inkluder = true)),
            Enhet.NASJONAL,
            null,
            null,
            mutableListOf(Saksbehandler("OJR", "OJR", "OJR", enhet = Enhet.NASJONAL.navn)),
            false,
            mutableListOf()
        )

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
            aksjonspunkter = Aksjonspunkter(emptyMap()),
            tilBeslutter = false,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false
        )

        val tilhørerOppgaveTilKø = oppgaveKø.tilhørerOppgaveTilKø(oppgave, null)
        Assert.assertTrue(tilhørerOppgaveTilKø)
    }
}
