package no.nav.k9.nyoppgavestyring.oppgavetype

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import no.nav.k9.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjon
import no.nav.k9.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.nyoppgavestyring.mottak.oppgavetype.Oppgavefelt
import no.nav.k9.nyoppgavestyring.mottak.oppgavetype.Oppgavetype
import no.nav.k9.nyoppgavestyring.mottak.oppgavetype.Oppgavetyper
import org.junit.jupiter.api.Test

class OppgavetypeTest {
    val område = Område(eksternId = "K9")

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
                            påkrevd = true
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
                            påkrevd = true
                        )
                    )
                )
            )
        )

        val (sletteListe, leggTilListe, oppdaterListe) = lagOppgavetyper().finnForskjell(innkomendeOppgavetyper)
        assertThat(sletteListe.oppgavetyper).hasSize(1)
        assertThat(leggTilListe.oppgavetyper).isEmpty()
        assertThat(oppdaterListe.oppgavetyper).isEmpty() //TODO sjekk oppdaterListe
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
                            feltDefinisjon = no.nav.k9.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjon(
                                eksternId = "saksnummer",
                                område = område,
                                listetype = false,
                                tolkesSom = "String",
                                visTilBruker = true
                            ),
                            visPåOppgave = true,
                            påkrevd = true
                        ),
                        Oppgavefelt(
                            feltDefinisjon = no.nav.k9.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjon(
                                eksternId = "saksnummer",
                                område = område,
                                listetype = false,
                                tolkesSom = "String",
                                visTilBruker = true
                            ),
                            visPåOppgave = true,
                            påkrevd = true
                        )
                    )
                ),
                Oppgavetype(
                    eksternId = "test",
                    område = område,
                    definisjonskilde = "k9-sak-til-los",
                    oppgavefelter = setOf(
                        Oppgavefelt(
                            feltDefinisjon = no.nav.k9.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjon(
                                eksternId = "saksnummer",
                                område = område,
                                listetype = false,
                                tolkesSom = "String",
                                visTilBruker = true
                            ),
                            visPåOppgave = true,
                            påkrevd = true
                        )
                    )
                )
            )
        )

        val (sletteListe, leggTilListe, oppdaterListe) = lagOppgavetyper().finnForskjell(innkommendeFeltdefinisjoner)
        assertThat(sletteListe.oppgavetyper).isEmpty()
        assertThat(leggTilListe.oppgavetyper).isEmpty()
        assertThat(oppdaterListe.oppgavetyper).hasSize(2)
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
                            feltDefinisjon = no.nav.k9.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjon(
                                eksternId = "saksnummer",
                                område = område,
                                listetype = false,
                                tolkesSom = "String",
                                visTilBruker = true
                            ),
                            visPåOppgave = true,
                            påkrevd = true
                        ),
                        Oppgavefelt(
                            feltDefinisjon = no.nav.k9.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjon(
                                eksternId = "saksnummer",
                                område = område,
                                listetype = false,
                                tolkesSom = "String",
                                visTilBruker = true
                            ),
                            visPåOppgave = true,
                            påkrevd = true
                        )
                    )
                ),
                Oppgavetype(
                    eksternId = "test",
                    område = område,
                    definisjonskilde = "k9-sak-til-los",
                    oppgavefelter = setOf(
                        Oppgavefelt(
                            feltDefinisjon = no.nav.k9.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjon(
                                eksternId = "saksnummer",
                                område = område,
                                listetype = false,
                                tolkesSom = "String",
                                visTilBruker = true
                            ),
                            visPåOppgave = true,
                            påkrevd = true
                        )
                    )
                )
            )
        )
    }

}