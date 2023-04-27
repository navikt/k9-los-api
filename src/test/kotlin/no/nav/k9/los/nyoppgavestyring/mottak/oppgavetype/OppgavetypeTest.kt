package no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjon
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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
                                visTilBruker = true,
                                kodeverkreferanse = null
                            ),
                            visPåOppgave = true,
                            påkrevd = true,
                            feltutleder = null,
                            defaultverdi = "defaultverdi"
                        ),
                        Oppgavefelt(
                            feltDefinisjon = Feltdefinisjon(
                                eksternId = "saksnummer",
                                område = område,
                                listetype = false,
                                tolkesSom = "String",
                                visTilBruker = true,
                                kodeverkreferanse = null
                            ),
                            visPåOppgave = true,
                            påkrevd = true,
                            feltutleder = null,
                            defaultverdi = "defaultverdi"
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
                                visTilBruker = true,
                                kodeverkreferanse = null
                            ),
                            visPåOppgave = true,
                            påkrevd = true,
                            feltutleder = null,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = Feltdefinisjon(
                                eksternId = "saksnummer",
                                område = område,
                                listetype = false,
                                tolkesSom = "String",
                                visTilBruker = true,
                                kodeverkreferanse = null
                            ),
                            visPåOppgave = true,
                            påkrevd = true,
                            feltutleder = null,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = Feltdefinisjon(
                                eksternId = "aktorId",
                                område = område,
                                listetype = false,
                                tolkesSom = "String",
                                visTilBruker = true,
                                kodeverkreferanse = null
                            ),
                            visPåOppgave = true,
                            påkrevd = true,
                            feltutleder = null,
                            defaultverdi = null
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
                                visTilBruker = true,
                                kodeverkreferanse = null
                            ),
                            visPåOppgave = true,
                            påkrevd = true,
                            feltutleder = null,
                            defaultverdi = "defaultverdi"
                        )
                    )
                )
            )
        )

        val (sletteListe, leggTilListe, oppdaterListe) = lagOppgavetyper().finnForskjell(innkommendeFeltdefinisjoner)
        assertThat(sletteListe.oppgavetyper).isEmpty()
        assertThat(leggTilListe.oppgavetyper).isEmpty()
        assertThat(oppdaterListe[0].felterSomSkalLeggesTil).hasSize(1)
        assertThat(oppdaterListe[0].felterSomSkalFjernes).hasSize(1)
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
                                visTilBruker = true,
                                kodeverkreferanse = null
                            ),
                            visPåOppgave = true,
                            påkrevd = true,
                            feltutleder = null,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = Feltdefinisjon(
                                eksternId = "saksnummer",
                                område = område,
                                listetype = false,
                                tolkesSom = "String",
                                visTilBruker = true,
                                kodeverkreferanse = null
                            ),
                            visPåOppgave = true,
                            påkrevd = true,
                            feltutleder = null,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = Feltdefinisjon(
                                eksternId = "testverdi",
                                område = område,
                                listetype = false,
                                tolkesSom = "String",
                                visTilBruker = true,
                                kodeverkreferanse = null
                            ),
                            visPåOppgave = true,
                            påkrevd = false,
                            feltutleder = null,
                            defaultverdi = "defaultverdi"
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
                                visTilBruker = true,
                                kodeverkreferanse = null
                            ),
                            visPåOppgave = true,
                            påkrevd = true,
                            feltutleder = null,
                            defaultverdi = null
                        )
                    )
                )
            )
        )
    }

}