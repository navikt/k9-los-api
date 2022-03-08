package no.nav.k9.domene.lager.oppgave.v2

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import no.nav.k9.AbstractPostgresTest
import no.nav.k9.buildAndTestConfig
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.modell.Fagsystem
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.get
import java.time.LocalDateTime
import java.util.*

internal class OppgaveTjenesteV2Test : AbstractPostgresTest(), KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create {
        modules(buildAndTestConfig(dataSource))
    }
    val eksternId = UUID.randomUUID()

    @Test
    fun `nyOppgave skal feil hvis det ikke finnes en behandling og det ikke er oppgitt en opprettelsefunksjon`() {
        val oppgaveTjenesteV2 = get<OppgaveTjenesteV2>()
        assertThrows(IllegalStateException::class.java) {
            oppgaveTjenesteV2.nyeOppgaveHendelser(
                eksternId.toString(),
                OpprettOppgave(
                    tidspunkt = LocalDateTime.now(),
                    oppgaveKode = "OPPGAVE1",
                    frist = null
                )
            )
        }
    }

    @Test
    fun `nyOppgave skal opprette behandling hvis det ikke finnes fra før, og det er oppgitt en opprettelsefunksjon`() {
        val oppgaveTjenesteV2 = get<OppgaveTjenesteV2>()
        val oppretteFunksjon = {
            Behandling.ny(
                eksternReferanse = eksternId.toString(),
                fagsystem = Fagsystem.K9SAK,
                ytelseType = FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
                "BT-004",
                Ident("12345", Ident.IdType.AKTØRID)
            )
        }
        oppgaveTjenesteV2.nyeOppgaveHendelser(
            eksternId.toString(),
            OpprettOppgave(
                tidspunkt = LocalDateTime.now(),
                oppgaveKode = "OPPGAVE1",
                frist = null
            ),
            oppretteFunksjon
        )
        assertThat(oppgaveTjenesteV2.hentEllerOpprettFra(eksternId.toString()).oppgaver()).hasSize(1)
    }


    @Test
    fun `Ferdigstille behandling skal lukke alle oppgavene`() {
        val oppgaveTjenesteV2 = get<OppgaveTjenesteV2>()
        val oppretteFunksjon = {
            Behandling.ny(
                eksternReferanse = eksternId.toString(),
                fagsystem = Fagsystem.K9SAK,
                ytelseType = FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
                "BT-004",
                Ident("12345", Ident.IdType.AKTØRID)
            )
        }
        oppgaveTjenesteV2.nyeOppgaveHendelser(
            eksternId.toString(),
            OpprettOppgave(
                tidspunkt = LocalDateTime.now(),
                oppgaveKode = "OPPGAVE1",
                frist = null
            ),
            oppretteFunksjon
        )
        assertThat(oppgaveTjenesteV2.hentEllerOpprettFra(eksternId.toString()).oppgaver()).hasSize(1)

        oppgaveTjenesteV2.nyeOppgaveHendelser(
            eksternId.toString(),
            FerdigstillBehandling(tidspunkt = LocalDateTime.now()),
        )
        assertThat(oppgaveTjenesteV2.hentEllerOpprettFra(eksternId.toString()).oppgaver()).hasSize(1)
        assertThat(oppgaveTjenesteV2.hentEllerOpprettFra(eksternId.toString()).oppgaver().map { it.erAktiv() }).containsOnly(false)
    }
}