package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjon
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavefelt
import org.junit.jupiter.api.Test

class AktivOppgaveRepositoryTest {
    @Test
    fun `når det er ingen forskjell skal diff være tom`() {
        val eksisterende = listOf(
            mockOppgaveFeltverdi("aksjonspunkt", listetype = true, "9001", id = 2),
            mockOppgaveFeltverdi("aksjonspunkt", listetype = true, "9003", id = 3),
        )
        val nye = listOf(
            mockOppgaveFeltverdi("aksjonspunkt", listetype = true, verdi = "9001"),
            mockOppgaveFeltverdi("aksjonspunkt", listetype = true, verdi = "9003")
        )
        val diffResultat = AktivOppgaveRepository.regnUtDiff(eksisterende, nye)
        assertThat(diffResultat.deletes).isEmpty()
        assertThat(diffResultat.updates).isEmpty()
        assertThat(diffResultat.inserts).isEmpty()

    }

    @Test
    fun `når det er ingen fra før skal alle nye elementer returneres som insert`() {
        val eksisterende = listOf<OppgaveFeltverdi>()
        val nye = listOf(
            mockOppgaveFeltverdi("aksjonspunkt", listetype = true, verdi = "9001"),
            mockOppgaveFeltverdi("aksjonspunkt", listetype = true, verdi = "9003")
        )
        val diffResultat = AktivOppgaveRepository.regnUtDiff(eksisterende, nye)
        assertThat(diffResultat.deletes).isEmpty()
        assertThat(diffResultat.updates).isEmpty()
        assertThat(diffResultat.inserts).containsOnly(
            AktivOppgaveRepository.Verdi( "9001", 1, "aksjonspunkt"),
            AktivOppgaveRepository.Verdi( "9003", 1, "aksjonspunkt"))
    }

    @Test
    fun `når det finnes gamle elementer men ingen nye skal alle slettes`() {
        val eksisterende = listOf(
            mockOppgaveFeltverdi("aksjonspunkt", listetype = true, "9001", id = 2),
            mockOppgaveFeltverdi("aksjonspunkt", listetype = true, "9003", id = 3),
        )
        val nye = listOf<OppgaveFeltverdi>()
        val diffResultat = AktivOppgaveRepository.regnUtDiff(eksisterende, nye)
        assertThat(diffResultat.deletes).containsOnly(2L, 3L)
        assertThat(diffResultat.updates).isEmpty()
        assertThat(diffResultat.inserts).isEmpty()
    }

    @Test
    fun `når det er endring på ett element skal det oppdateres `() {
        val eksisterende = listOf(
            mockOppgaveFeltverdi("aksjonspunkt", listetype = true, "9001", id = 2),
            mockOppgaveFeltverdi("aksjonspunkt", listetype = true, "9003", id = 3),
        )
        val nye = listOf(
            mockOppgaveFeltverdi("aksjonspunkt", listetype = true, verdi = "9001"),
            mockOppgaveFeltverdi("aksjonspunkt", listetype = true, verdi = "1111")
        )
        val diffResultat = AktivOppgaveRepository.regnUtDiff(eksisterende, nye)
        assertThat(diffResultat.deletes).isEmpty()
        assertThat(diffResultat.updates).containsOnly(Pair(3L, AktivOppgaveRepository.Verdi("1111", 1, "aksjonspunkt")))
        assertThat(diffResultat.inserts).isEmpty()
    }

    @Test
    fun `grisecase`() {
        val eksisterende = listOf(
            mockOppgaveFeltverdi(eksternId = "aksjonspunkt", oppgavefeltId = 1, listetype = true, verdi = "9001", id = 2),
            mockOppgaveFeltverdi(eksternId = "aksjonspunkt", oppgavefeltId = 1, listetype = true, verdi = "9002", id = 3),
            mockOppgaveFeltverdi(eksternId = "aksjonspunkt", oppgavefeltId = 1, listetype = true, verdi = "9003", id = 4),
            mockOppgaveFeltverdi(eksternId = "test", oppgavefeltId = 2, listetype = false, verdi = "test", id = 5),
        )
        val nye = listOf(
            mockOppgaveFeltverdi(eksternId = "aksjonspunkt", oppgavefeltId = 1, listetype = true, verdi = "9001"),
            mockOppgaveFeltverdi(eksternId = "aksjonspunkt", oppgavefeltId = 1, listetype = true, verdi = "1111"),
            mockOppgaveFeltverdi(eksternId = "test2", oppgavefeltId = 3, listetype = false, verdi = "test2"),
        )
        val diffResultat = AktivOppgaveRepository.regnUtDiff(eksisterende, nye)
        assertThat(diffResultat.deletes.size).isEqualTo(2)
        assertThat(diffResultat.updates.entries.size).isEqualTo(1)
        assertThat(diffResultat.updates.values.first()).isEqualTo(AktivOppgaveRepository.Verdi("1111", 1, "aksjonspunkt"))
        //vi bryr oss ikke om hvilke av aksjonspunktverdiene (3 og 4) som gjenbrukes, men en skal slettes og en skal oppdateres
        assertThat(diffResultat.updates.keys + diffResultat.deletes).containsOnly(3L, 4L, 5L)
        assertThat(diffResultat.inserts).containsOnly(AktivOppgaveRepository.Verdi("test2", 3, "test2"))
    }

    fun mockOppgaveFeltverdi(eksternId: String, listetype: Boolean, verdi: String, oppgavefeltId: Long = 1, id : Long? = null): OppgaveFeltverdi {
        return OppgaveFeltverdi(
            id = id,
            oppgavefelt = mockOppgavefelt(eksternId, listetype, oppgavefeltId),
            verdi = verdi
        )
    }

    private fun mockOppgavefelt(
        eksternId: String,
        listetype: Boolean,
        oppgavefeltId: Long
    ) = Oppgavefelt(
        id = oppgavefeltId,
        feltDefinisjon = mockFeltdefinisjon(eksternId, listetype),
        visPåOppgave = false,
        påkrevd = false,
        defaultverdi = null,
        feltutleder = null
    )

    fun mockFeltdefinisjon(eksternId: String, listetype: Boolean): Feltdefinisjon {
        return Feltdefinisjon(
            eksternId = eksternId,
            område = mockOmråde(),
            visningsnavn = "",
            listetype = listetype,
            tolkesSom = "",
            visTilBruker = false,
            kokriterie = false,
            kodeverkreferanse = null,
            transientFeltutleder = null,
        )
    }
    fun mockOmråde(): Område {
        return Område(eksternId = "test")
    }
}