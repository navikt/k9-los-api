package no.nav.k9.los.domene.lager.oppgave.v2

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDateTime
import java.util.*

internal class OppgaveTjenesteV2Test : AbstractK9LosIntegrationTest() {
    val eksternId1 = UUID.randomUUID().toString()
    val eksternId2 = UUID.randomUUID().toString()

    val BEHANDLINGENDRET1 = BehandlingEndret(
            tidspunkt = LocalDateTime.now(),
            eksternReferanse = eksternId1,
            fagsystem = Fagsystem.K9SAK,
            ytelseType = FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
            "BT-004",
            Ident("12345", Ident.IdType.AKTØRID),
        )
    val BEHANDLINGENDRET2 = BehandlingEndret(
        tidspunkt = LocalDateTime.now(),
        eksternReferanse = eksternId2,
        fagsystem = Fagsystem.K9SAK,
        ytelseType = FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
        "BT-004",
        Ident("54321", Ident.IdType.AKTØRID),
    )
    val OPPGAVE1 = OpprettOppgave(tidspunkt = LocalDateTime.now(), oppgaveKode = "OPPGAVE1", frist = null)
    val OPPGAVE2 = OpprettOppgave(tidspunkt = LocalDateTime.now(), oppgaveKode = "OPPGAVE2", frist = null)

    @Test
    fun `nyOppgave skal feil hvis det ikke finnes en behandling og det ikke er oppgitt en opprettelsefunksjon`() {
        val oppgaveTjenesteV2 = get<OppgaveTjenesteV2>()
        assertThrows(IllegalStateException::class.java) {
            oppgaveTjenesteV2.nyOppgaveHendelse(eksternId1, OPPGAVE1)
        }
    }

    @Test
    fun `nyOppgave skal opprette behandling hvis det ikke finnes fra før, og det er oppgitt en opprettelsefunksjon`() {
        val oppgaveTjenesteV2 = get<OppgaveTjenesteV2>()
        val oppgaveRepository = get<OppgaveRepositoryV2>()

        val behandlingEndret = BehandlingEndret(
                tidspunkt = LocalDateTime.now(),
                eksternReferanse = eksternId1,
                fagsystem = Fagsystem.K9SAK,
                ytelseType = FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
                behandlingType = "BT-004",
                søkersId = Ident("12345", Ident.IdType.AKTØRID)
            )

        oppgaveTjenesteV2.nyeOppgaveHendelser(eksternId1, listOf(behandlingEndret, OPPGAVE1))
        assertThat(oppgaveRepository.hentBehandling(eksternId1)!!.oppgaver()).hasSize(1)
    }


    @Test
    fun `nyOppgave skal ignoreres hvis det allerede finnes en aktiv med samme kode på referansen`() {
        val oppgaveTjenesteV2 = get<OppgaveTjenesteV2>()
        val oppgaveRepository = get<OppgaveRepositoryV2>()

        val behandlingEndret =
            BehandlingEndret(
                tidspunkt = LocalDateTime.now(),
                eksternReferanse = eksternId1,
                fagsystem = Fagsystem.K9SAK,
                ytelseType = FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
                "BT-004",
                Ident("12345", Ident.IdType.AKTØRID),
            )
        oppgaveTjenesteV2.nyeOppgaveHendelser(eksternId1, listOf(behandlingEndret, OPPGAVE1))
        oppgaveTjenesteV2.nyeOppgaveHendelser(eksternId1, listOf(OPPGAVE1))
        assertThat(oppgaveRepository.hentBehandling(eksternId1)!!.oppgaver()).hasSize(1)
    }

    @Test
    fun `nyOppgave skal kunne ha to aktive oppgaver med ulik oppgavekode på samme referanse`() {
        val oppgaveTjenesteV2 = get<OppgaveTjenesteV2>()
        val oppgaveRepository = get<OppgaveRepositoryV2>()

        oppgaveTjenesteV2.nyeOppgaveHendelser(eksternId1, listOf(BEHANDLINGENDRET1, OPPGAVE1))
        oppgaveTjenesteV2.nyeOppgaveHendelser(eksternId1, listOf(OPPGAVE2))
        assertThat(oppgaveRepository.hentBehandling(eksternId1)!!.oppgaver()).hasSize(2)
    }

    @Test
    fun `nyHendelse skal kunne motta flere eventer`() {
        val oppgaveTjenesteV2 = get<OppgaveTjenesteV2>()
        val oppgaveRepository = get<OppgaveRepositoryV2>()

        oppgaveTjenesteV2.nyeOppgaveHendelser(eksternId1, listOf(BEHANDLINGENDRET1, OPPGAVE1))
        oppgaveTjenesteV2.nyOppgaveHendelse(eksternId1, BEHANDLINGENDRET1)
        oppgaveTjenesteV2.nyeOppgaveHendelser(eksternId1, listOf(OPPGAVE2))

        oppgaveTjenesteV2.nyOppgaveHendelse(eksternId2, BEHANDLINGENDRET2)
        oppgaveTjenesteV2.nyOppgaveHendelse(eksternId2, OPPGAVE2)
        oppgaveTjenesteV2.nyOppgaveHendelse(eksternId2, BEHANDLINGENDRET2)
        oppgaveTjenesteV2.nyOppgaveHendelse(eksternId2, BEHANDLINGENDRET2)
        oppgaveTjenesteV2.nyOppgaveHendelse(eksternId2, OPPGAVE1)

        assertThat(oppgaveRepository.hentBehandling(eksternId1)!!.oppgaver()).hasSize(2)
        assertThat(oppgaveRepository.hentBehandling(eksternId2)!!.oppgaver()).hasSize(2)
    }

    @Test
    fun `Ferdigstille behandling skal lukke alle oppgavene`() {
        val oppgaveTjenesteV2 = get<OppgaveTjenesteV2>()
        val oppgaveRepository = get<OppgaveRepositoryV2>()

        oppgaveTjenesteV2.nyeOppgaveHendelser(eksternId1, listOf(BEHANDLINGENDRET1, OPPGAVE1))
        assertThat(oppgaveRepository.hentBehandling(eksternId1)!!.oppgaver()).hasSize(1)

        oppgaveTjenesteV2.nyOppgaveHendelse(eksternId1, FerdigstillBehandling(tidspunkt = LocalDateTime.now()))
        assertThat(oppgaveRepository.hentBehandling(eksternId1)!!.oppgaver()).hasSize(1)
        assertThat(oppgaveRepository.hentBehandling(eksternId1)!!.oppgaver().map { it.erAktiv() }).containsOnly(false)
    }
}