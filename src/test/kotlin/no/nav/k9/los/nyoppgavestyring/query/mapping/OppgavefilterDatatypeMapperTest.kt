package no.nav.k9.los.nyoppgavestyring.query.mapping

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import no.nav.k9.los.nyoppgavestyring.query.db.OmrådeOgKode
import no.nav.k9.los.nyoppgavestyring.query.db.OppgavefeltMedMer
import no.nav.k9.los.nyoppgavestyring.query.dto.felter.Oppgavefelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class OppgavefilterDatatypeMapperTest {
    val felter = mapOf(
        OmrådeOgKode("K9", "testfeltBoolean") to OppgavefeltMedMer(
            oppgavefelt = Oppgavefelt(
                område = "K9",
                kode = "testfeltBoolean",
                visningsnavn = "Testet felt",
                tolkes_som = "boolean",
                kokriterie = true,
                verdiforklaringerErUttømmende = false,
                verdiforklaringer = null
            ),
            transientFeltutleder = null
        ),
        OmrådeOgKode("K9", "testfeltLDT") to OppgavefeltMedMer(
            oppgavefelt = Oppgavefelt(
                område = "K9",
                kode = "testfeltLDT",
                visningsnavn = "Testet felt",
                tolkes_som = "Timestamp",
                kokriterie = true,
                verdiforklaringerErUttømmende = false,
                verdiforklaringer = null
            ),
            transientFeltutleder = null
        ),
        OmrådeOgKode("K9", "testfeltInteger") to OppgavefeltMedMer(
            oppgavefelt = Oppgavefelt(
                område = "K9",
                kode = "testfeltInteger",
                visningsnavn = "Testet felt",
                tolkes_som = "Integer",
                kokriterie = true,
                verdiforklaringerErUttømmende = false,
                verdiforklaringer = null
            ),
            transientFeltutleder = null
        )
    )

    @Nested
    @Disabled("Kun nyttig hvis det skal være egen kolonne for verdi_boolean, slett dersom string holder")
    inner class Boolean {
        @Test
        fun `skal mappe til true`() {
            val mappet = OppgavefilterDatatypeMapper.map(
                felter, listOf(FeltverdiOppgavefilter(område = "K9", kode = "testfeltBoolean", EksternFeltverdiOperator.EQUALS, listOf("true")))
            )
            assertThat((mappet[0] as FeltverdiOppgavefilter).verdi[0]).isEqualTo(true)
        }

        @Test
        fun `skal mappe boolean til false`() {
            val mappet = OppgavefilterDatatypeMapper.map(
                felter, listOf(FeltverdiOppgavefilter(område = "K9", kode = "testfeltBoolean", EksternFeltverdiOperator.EQUALS, listOf("false")))
            )
            assertThat((mappet[0] as FeltverdiOppgavefilter).verdi[0]).isEqualTo(false)
        }

        @Test
        fun `skal mappe til null`() {
            val mappet = OppgavefilterDatatypeMapper.map(
                felter, listOf(FeltverdiOppgavefilter(område = "K9", kode = "testfeltBoolean", EksternFeltverdiOperator.EQUALS, listOf(null)))
            )
            assertThat((mappet[0] as FeltverdiOppgavefilter).verdi[0]).isNull()
        }
    }

    @Nested
    @Disabled("Kun nyttig hvis det skal være egen kolonne for verdi_date, slett dersom string holder")
    inner class LocalDateTime {
        @Test
        fun `skal mappe til riktig tidspunkt`() {
            val mappet = OppgavefilterDatatypeMapper.map(
                felter, listOf(FeltverdiOppgavefilter(område = "K9", kode = "testfeltLDT", EksternFeltverdiOperator.EQUALS, listOf("2020-01-01T12:34:56.789")))
            )
            assertThat((mappet[0] as FeltverdiOppgavefilter).verdi[0]).isEqualTo(java.time.LocalDateTime.of(2020, 1, 1, 12, 34, 56, 789000000))
        }
    }

    @Nested
    inner class Integer {
        @Test
        fun `skal mappe til riktig tall`() {
            val mappet = OppgavefilterDatatypeMapper.map(
                felter, listOf(FeltverdiOppgavefilter(område = "K9", kode = "testfeltInteger", EksternFeltverdiOperator.EQUALS, listOf("2020202020")))
            )
            assertThat((mappet[0] as FeltverdiOppgavefilter).verdi[0]).isEqualTo(2020202020L)
        }
    }

}