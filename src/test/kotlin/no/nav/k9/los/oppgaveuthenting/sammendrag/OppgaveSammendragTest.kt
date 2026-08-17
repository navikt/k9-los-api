package no.nav.k9.los.oppgaveuthenting.sammendrag

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.infrastruktur.pdl.IPdlService
import no.nav.k9.los.infrastruktur.pdl.PersonPdl
import no.nav.k9.los.infrastruktur.pdl.PersonPdlResponse
import no.nav.k9.los.kodeverk.BehandlingStatus
import no.nav.k9.los.kodeverk.BehandlingType
import no.nav.k9.los.kodeverk.FagsakYtelseType
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.omraade.Område
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.Oppgavetype
import no.nav.k9.los.oppgaveuthenting.omraade.Oppgavesøkere
import no.nav.k9.los.oppgaveuthenting.omraade.k9.K9Oppgavesøk
import no.nav.k9.los.oppgaveuthenting.omraade.ung.UngOppgavesøk
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.oppgaveuthenting.Oppgavefelt
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class OppgaveSammendragTest {
    @Test
    fun `K9-adapter mapper faste sammendragsfelt`() {
        val resultat = K9Oppgavesøk().tilSammendrag(oppgave("K9"), person())

        assertThat(resultat.oppgaveNøkkel.oppgaveEksternId).isEqualTo("oppgave-1")
        assertThat(resultat.person?.navn).isEqualTo("Ola Nordmann")
        assertThat(resultat.ytelse).isEqualTo(KodeOgNavnDto("PSB", "Pleiepenger sykt barn"))
        assertThat(resultat.behandlingstype).isEqualTo(KodeOgNavnDto("BT-002", "Førstegangsbehandling"))
        assertThat(resultat.oppgavestatus).isEqualTo(KodeOgNavnDto("AAPEN", "Åpen"))
        assertThat(resultat.behandlingsstatus).isEqualTo(KodeOgNavnDto("UTRED", "Utredes"))
        assertThat(resultat.hastesak).isTrue()
    }

    @Test
    fun `builder henter samme person bare en gang per respons`() = runBlocking {
        val pdlService = mockk<IPdlService>()
        val person = person()
        coEvery { pdlService.person("aktor-1") } returns PersonPdlResponse(false, person)
        val builder = OppgaveSammendragDtoBuilder(Oppgavesøkere(K9Oppgavesøk(), UngOppgavesøk()), pdlService)

        val resultat = builder.bygg(listOf(oppgave("K9"), oppgave("K9", "oppgave-2")))

        assertThat(resultat.size).isEqualTo(2)
        coVerify(exactly = 1) { pdlService.person("aktor-1") }
    }

    @Test
    fun `builder bruker allerede hentet person`() = runBlocking {
        val pdlService = mockk<IPdlService>()
        val builder = OppgaveSammendragDtoBuilder(Oppgavesøkere(K9Oppgavesøk(), UngOppgavesøk()), pdlService)

        val resultat = builder.bygg(listOf(oppgave("K9")), mapOf("aktor-1" to person()))

        assertThat(resultat.single().person?.fnr).isEqualTo("12345678901")
        coVerify(exactly = 0) { pdlService.person(any()) }
    }

    @Test
    fun `builder faller ikke tilbake til K9 når område mangler adapter`() {
        val builder = OppgaveSammendragDtoBuilder(Oppgavesøkere(K9Oppgavesøk(), UngOppgavesøk()), mockk())

        val feil = assertThrows<NotImplementedError> {
            runBlocking { builder.bygg(listOf(oppgave("UNG"))) }
        }

        assertThat(feil.message ?: "").contains("UNG")
    }

    private fun oppgave(område: String, eksternId: String = "oppgave-1"): Oppgave {
        val oppgavetype = mockk<Oppgavetype>()
        every { oppgavetype.eksternId } returns "k9sak"
        every { oppgavetype.område } returns Område(id = 1, eksternId = område)
        every { oppgavetype.oppgavebehandlingsUrlTemplate } returns null
        return Oppgave(
            eksternId = eksternId,
            eksternVersjon = "1",
            reservasjonsnøkkel = "reservasjon-1",
            oppgavetype = oppgavetype,
            status = Oppgavestatus.AAPEN,
            endretTidspunkt = LocalDateTime.parse("2026-08-12T10:00:00"),
            felter = mapOf(
                "aktorId" to "aktor-1",
                "ytelsestype" to FagsakYtelseType.PLEIEPENGER_SYKT_BARN.kode,
                "behandlingTypekode" to BehandlingType.FORSTEGANGSSOKNAD.kode,
                "behandlingsstatus" to BehandlingStatus.UTREDES.kode,
                "saksnummer" to "SAK-1",
                "journalpostId" to "123456789",
                "fagsakÅr" to "2026",
                "registrertDato" to "2026-08-12T09:00:00",
                "hastesak" to "true",
            ).map { (kode, verdi) -> Oppgavefelt(kode, område, false, false, verdi, null) },
        )
    }

    private fun person() = PersonPdl(
        PersonPdl.Data(
            PersonPdl.Data.HentPerson(
                folkeregisteridentifikator = listOf(PersonPdl.Data.HentPerson.Folkeregisteridentifikator("12345678901")),
                navn = listOf(PersonPdl.Data.HentPerson.Navn("Nordmann", "Ola Nordmann", "Ola", null)),
                kjoenn = listOf(PersonPdl.Data.HentPerson.Kjoenn("MANN")),
                doedsfall = emptyList(),
            )
        )
    )
}
