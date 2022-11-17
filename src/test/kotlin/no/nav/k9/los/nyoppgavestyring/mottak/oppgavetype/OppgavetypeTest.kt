package no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavefelt
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavetype
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjon
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavetyper
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import kotlin.test.assertFailsWith

class OppgavetypeTest {
    private val område = Område(eksternId = "K9")

    @Test
    fun `test at det ikke er mulig å opprette oppgavetyper på tvers av områder`() {
        val innkommendeOppgavetyper = lagOppgavetyper()

        assertThrows<IllegalStateException>("Kan ikke sammenligne oppgavetyper på tvers av områder") {
            Oppgavetyper(
                område = Område(eksternId = "ikke-k9"),
                emptySet()
            ).finnForskjell(innkommendeOppgavetyper)
        }
    }

    @Test
    fun `test at det ikke er mulig å opprette oppgavetyper på tvers av definisjonskilder`() {
        val innkommendeOppgavetyper = lagOppgavetyper()

        assertThrows<IllegalStateException>("Kan ikke sammenligne oppgavetyper på tvers av definisjonskilder") {
            Oppgavetyper(
                område = område,
                setOf(
                    Oppgavetype(
                        eksternId = "test",
                        område = område,
                        definisjonskilde = "ikke-k9-sak-til-los",
                        oppgavefelter = setOf()
                    )
                )
            ).finnForskjell(innkommendeOppgavetyper)
        }
    }

    @Test
    fun `test at vi legger til oppgavetyper om de ikke finnes fra før`() {
        val innkommendeOppgavetyper = lagOppgavetyper()

        val (sletteListe, leggTilListe, oppdaterListe) = Oppgavetyper(
            område = område,
            emptySet()
        ).finnForskjell(innkommendeOppgavetyper)

        assertThat(sletteListe.oppgavetyper).isEmpty()
        assertThat(leggTilListe.oppgavetyper).hasSize(2)
        //assertThat(oppdaterListe.oppgavetyper).isEmpty() TODO sjekk oppdaterListe
    }

    @Test
    fun `test at vi sletter en oppgavetype dersom den ikke finnes i dto men er persistert`() {
        val innkomendeOppgavetyper = Oppgavetyper(
            område = område,
            oppgavetyper = setOf(
                Oppgavetype(
                    eksternId = "aksjonspunkt",
                    område = område,
                    definisjonskilde = "k9-sak-til-los",
                    oppgavefelter = setOf(
                        Oppgavefelt(
                            feltDefinisjon =
                            Feltdefinisjon(
                                eksternId = "saksnummer",
                                område = område,
                                listetype = false,
                                tolkesSom = "String",
                                visTilBruker = true
                            ),
                            visPåOppgave = true,
                            påkrevd = true,
                            feltutleder = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = Feltdefinisjon(
                                eksternId = "saksnummer",
                                område = område,
                                listetype = false,
                                tolkesSom = "String",
                                visTilBruker = true
                            ),
                            visPåOppgave = true,
                            påkrevd = true,
                            feltutleder = null // TODO
                        )
                    )
                )
            )
        )

        val (sletteListe, leggTilListe, oppdaterListe) = lagOppgavetyper().finnForskjell(innkomendeOppgavetyper)
        assertThat(sletteListe.oppgavetyper).hasSize(1)
        assertThat(leggTilListe.oppgavetyper).isEmpty()
        //assertThat(oppdaterListe.oppgavetyper).isEmpty() //TODO sjekk oppdaterListe
    }

    @Disabled("I påvente av funksjonalitet for å kunne oppdatere oppgavetyper, i stedet for å måtte opprette de på nytt")
    @Test
    fun `test at vi legger feltdefinisjoner i opppdaterListe om de har endringer`() {
        val innkommendeFeltdefinisjoner = Oppgavetyper(
            område = område,
            oppgavetyper = setOf(
                Oppgavetype(
                    eksternId = "aksjonspunkt",
                    område = område,
                    definisjonskilde = "k9-sak-til-los",
                    oppgavefelter = setOf(
                        Oppgavefelt(
                            feltDefinisjon = Feltdefinisjon(
                                eksternId = "saksnummer",
                                område = område,
                                listetype = false,
                                tolkesSom = "String",
                                visTilBruker = true
                            ),
                            visPåOppgave = true,
                            påkrevd = true,
                            feltutleder = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = Feltdefinisjon(
                                eksternId = "saksnummer",
                                område = område,
                                listetype = false,
                                tolkesSom = "String",
                                visTilBruker = true
                            ),
                            visPåOppgave = true,
                            påkrevd = true,
                            feltutleder = null
                        )
                    )
                ),
                Oppgavetype(
                    eksternId = "test",
                    område = område,
                    definisjonskilde = "k9-sak-til-los",
                    oppgavefelter = setOf(
                        Oppgavefelt(
                            feltDefinisjon = Feltdefinisjon(
                                eksternId = "saksnummer",
                                område = område,
                                listetype = false,
                                tolkesSom = "String",
                                visTilBruker = true
                            ),
                            visPåOppgave = true,
                            påkrevd = true,
                            feltutleder = null
                        )
                    )
                )
            )
        )

        val (sletteListe, leggTilListe, oppdaterListe) = lagOppgavetyper().finnForskjell(innkommendeFeltdefinisjoner)
        assertThat(sletteListe.oppgavetyper).isEmpty()
        assertThat(leggTilListe.oppgavetyper).isEmpty()
        //assertThat(oppdaterListe.oppgavetyper.).hasSize(2)
    }

    private fun lagOppgavetyper(): Oppgavetyper {
        return Oppgavetyper(
            område = område,
            oppgavetyper = setOf(
                Oppgavetype(
                    eksternId = "aksjonspunkt",
                    område = område,
                    definisjonskilde = "k9-sak-til-los",
                    oppgavefelter = setOf(
                        Oppgavefelt(
                            feltDefinisjon = Feltdefinisjon(
                                eksternId = "saksnummer",
                                område = område,
                                listetype = false,
                                tolkesSom = "String",
                                visTilBruker = true
                            ),
                            visPåOppgave = true,
                            påkrevd = true,
                            feltutleder = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = Feltdefinisjon(
                                eksternId = "saksnummer",
                                område = område,
                                listetype = false,
                                tolkesSom = "String",
                                visTilBruker = true
                            ),
                            visPåOppgave = true,
                            påkrevd = true,
                            feltutleder = null
                        )
                    )
                ),
                Oppgavetype(
                    eksternId = "test",
                    område = område,
                    definisjonskilde = "k9-sak-til-los",
                    oppgavefelter = setOf(
                        Oppgavefelt(
                            feltDefinisjon = Feltdefinisjon(
                                eksternId = "saksnummer",
                                område = område,
                                listetype = false,
                                tolkesSom = "String",
                                visTilBruker = true
                            ),
                            visPåOppgave = true,
                            påkrevd = true,
                            feltutleder = null
                        )
                    )
                )
            )
        )
    }

}