package no.nav.k9.los.nyoppgavestyring.lagretsok

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class LagretSøkTest {

    private val saksbehandler = Saksbehandler(
        id = 123L,
        brukerIdent = "test",
        navn = "Test Testersen",
        epost = "test@nav.no",
        reservasjoner = mutableSetOf(),
        enhet = null
    )

    private val annenSaksbehandler = Saksbehandler(
        id = 456L,
        brukerIdent = "annen",
        navn = "Annen Testersen",
        epost = "annen@nav.no",
        reservasjoner = mutableSetOf(),
        enhet = null
    )

    @Test
    fun `opprettSøk skal opprette nytt søk med riktige verdier`() {
        val opprettLagretSøk = OpprettLagretSøk(
            tittel = "Test søk",
            beskrivelse = "Dette er et testsøk"
        )

        val lagretSøk = LagretSøk.opprettSøk(opprettLagretSøk, saksbehandler)

        assertThat(lagretSøk.id).isEqualTo(null)
        assertThat(lagretSøk.lagetAv).isEqualTo(123L)
        assertThat(lagretSøk.versjon).isEqualTo(1)
        assertThat(lagretSøk.tittel).isEqualTo("Test søk")
        assertThat(lagretSøk.beskrivelse).isEqualTo("Dette er et testsøk")
    }

    @Test
    fun `opprettSøk skal kaste exception hvis saksbehandler mangler id`() {
        val saksbehandlerUtenId = Saksbehandler(
            id = null,
            brukerIdent = "test",
            navn = "Test Testersen",
            epost = "test@nav.no",
            reservasjoner = mutableSetOf(),
            enhet = null
        )

        val opprettLagretSøk = OpprettLagretSøk(
            tittel = "Test søk",
            beskrivelse = "Dette er et testsøk"
        )

        assertThrows<IllegalStateException> {
            LagretSøk.opprettSøk(opprettLagretSøk, saksbehandlerUtenId)
        }
    }

    @Test
    fun `endre skal oppdatere søk med nye verdier og inkrementere versjon`() {
        val opprettLagretSøk = OpprettLagretSøk(
            tittel = "Opprinnelig tittel",
            beskrivelse = "Opprinnelig beskrivelse"
        )

        val lagretSøk = LagretSøk.opprettSøk(opprettLagretSøk, saksbehandler)
        val nyQuery = OppgaveQuery()

        val endreLagretSøk = EndreLagretSøk(
            id = 1L,
            tittel = "Ny tittel",
            beskrivelse = "Ny beskrivelse",
            versjon = lagretSøk.versjon,
            query = nyQuery
        )

        lagretSøk.endre(endreLagretSøk, saksbehandler)

        assertThat(lagretSøk.versjon).isEqualTo(2)
        assertThat(lagretSøk.tittel).isEqualTo("Ny tittel")
        assertThat(lagretSøk.beskrivelse).isEqualTo("Ny beskrivelse")
        assertThat(lagretSøk.query).isEqualTo(nyQuery)
    }

    @Test
    fun `endre skal kaste exception hvis annen saksbehandler prøver å endre`() {
        val opprettLagretSøk = OpprettLagretSøk(
            tittel = "Test søk",
            beskrivelse = "Dette er et testsøk"
        )

        val lagretSøk = LagretSøk.opprettSøk(opprettLagretSøk, saksbehandler)

        val endreLagretSøk = EndreLagretSøk(
            id = 1L,
            tittel = "Ny tittel",
            beskrivelse = "Ny beskrivelse",
            query = OppgaveQuery(),
            versjon = lagretSøk.versjon
        )

        val exception = assertThrows<IllegalStateException> {
            lagretSøk.endre(endreLagretSøk, annenSaksbehandler)
        }

        assertThat(exception.message).isEqualTo("Kan ikke endre lagret søk som ikke er opprettet av seg selv")
    }

    @Test
    fun `sjekkOmKanSlette skal ikke kaste exception for eier`() {
        val opprettLagretSøk = OpprettLagretSøk(
            tittel = "Test søk",
            beskrivelse = "Dette er et testsøk"
        )

        val lagretSøk = LagretSøk.opprettSøk(opprettLagretSøk, saksbehandler)

        // Skal ikke kaste exception
        lagretSøk.sjekkOmKanSlette(saksbehandler)
    }

    @Test
    fun `sjekkOmKanSlette skal kaste exception hvis annen saksbehandler prøver å slette`() {
        val opprettLagretSøk = OpprettLagretSøk(
            tittel = "Test søk",
            beskrivelse = "Dette er et testsøk"
        )

        val lagretSøk = LagretSøk.opprettSøk(opprettLagretSøk, saksbehandler)

        val exception = assertThrows<IllegalStateException> {
            lagretSøk.sjekkOmKanSlette(annenSaksbehandler)
        }

        assertThat(exception.message).isEqualTo("Kan ikke slette lagret søk som ikke er opprettet av seg selv")
    }

    @Test
    fun `equals skal være false mot null`() {
        val lagretSøk = LagretSøk.opprettSøk(OpprettLagretSøk("Test", "Beskrivelse"), saksbehandler)

        assertThat(lagretSøk.equals(null)).isFalse()
    }

    @Test
    fun `equals skal være false mot annen type`() {
        val lagretSøk = LagretSøk.opprettSøk(OpprettLagretSøk("Test", "Beskrivelse"), saksbehandler)

        assertThat(lagretSøk.equals("string")).isFalse()
    }

    @Test
    fun `equals skal være true for objekter med samme id når begge har id`() {
        val lagretSøk1 = LagretSøk.fraEksisterende(1L, 123L, 1L, "Tittel1", "Beskrivelse1", LocalDateTime.now())
        val lagretSøk2 = LagretSøk.fraEksisterende(1L, 123L, 2L, "Tittel2", "Beskrivelse2", LocalDateTime.now())

        assertThat(lagretSøk1).isEqualTo(lagretSøk2)
    }

    @Test
    fun `equals skal være false for objekter med forskjellig id`() {
        val lagretSøk1 = LagretSøk.fraEksisterende(1L, 123L, 1L, "Tittel", "Beskrivelse", LocalDateTime.now())
        val lagretSøk2 = LagretSøk.fraEksisterende(2L, 123L, 1L, "Tittel", "Beskrivelse", LocalDateTime.now())

        assertThat(lagretSøk1).isNotEqualTo(lagretSøk2)
    }

    @Test
    fun `equals skal være false når ett objekt har id og det andre ikke`() {
        val lagretSøkUtenId = LagretSøk.opprettSøk(OpprettLagretSøk("Test", "Beskrivelse"), saksbehandler)
        val lagretSøkMedId = LagretSøk.fraEksisterende(1L, 123L, 1L, "Test", "Beskrivelse", LocalDateTime.now())

        assertThat(lagretSøkUtenId).isNotEqualTo(lagretSøkMedId)
        assertThat(lagretSøkMedId).isNotEqualTo(lagretSøkUtenId)
    }

    @Test
    fun `equals skal bruke object identity for objekter uten id`() {
        val lagretSøk1 = LagretSøk.opprettSøk(OpprettLagretSøk("Test", "Beskrivelse"), saksbehandler)
        val lagretSøk2 = LagretSøk.opprettSøk(OpprettLagretSøk("Test", "Beskrivelse"), saksbehandler)

        // To forskjellige objekter uten id skal ikke være like
        assertThat(lagretSøk1).isNotEqualTo(lagretSøk2)
    }

    @Test
    fun `hashCode skal være konsistent med equals for objekter med id`() {
        val lagretSøk1 = LagretSøk.fraEksisterende(1L, 123L, 1L, "Tittel1", "Beskrivelse1", LocalDateTime.now())
        val lagretSøk2 = LagretSøk.fraEksisterende(1L, 123L, 2L, "Tittel2", "Beskrivelse2", LocalDateTime.now())

        assertThat(lagretSøk1.hashCode()).isEqualTo(lagretSøk2.hashCode())
    }

    @Test
    fun `hashCode skal være forskjellig for objekter med forskjellig id`() {
        val lagretSøk1 = LagretSøk.fraEksisterende(1L, 123L, 1L, "Tittel", "Beskrivelse", LocalDateTime.now())
        val lagretSøk2 = LagretSøk.fraEksisterende(2L, 123L, 1L, "Tittel", "Beskrivelse", LocalDateTime.now())

        assertThat(lagretSøk1.hashCode()).isNotEqualTo(lagretSøk2.hashCode())
    }

    @Test
    fun `hashCode skal bruke object identity for objekter uten id`() {
        val lagretSøk1 = LagretSøk.opprettSøk(OpprettLagretSøk("Test", "Beskrivelse"), saksbehandler)
        val lagretSøk2 = LagretSøk.opprettSøk(OpprettLagretSøk("Test", "Beskrivelse"), saksbehandler)

        // To forskjellige objekter uten id skal ha forskjellig hashCode
        assertThat(lagretSøk1.hashCode()).isNotEqualTo(lagretSøk2.hashCode())
    }
}