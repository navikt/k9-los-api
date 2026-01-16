package no.nav.k9.los.nyoppgavestyring.query

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator

class OppgaveQueryBeskrivelseSpec : FreeSpec({

    "lagBeskrivelse" - {
        "skal returnere 'Alle oppgaver' for tom query" {
            val query = OppgaveQuery()
            lagBeskrivelse(query) shouldBe "Alle oppgaver"
        }

        "oppgavestatus" - {
            "skal returnere 'Alle oppgaver' når alle statuser er valgt" {
                val query = OppgaveQuery(
                    filtere = listOf(
                        FeltverdiOppgavefilter(
                            område = null,
                            kode = "oppgavestatus",
                            operator = EksternFeltverdiOperator.IN,
                            verdi = Oppgavestatus.entries.map { it.kode }
                        )
                    )
                )
                lagBeskrivelse(query) shouldBe "Alle oppgaver"
            }

            "skal beskrive én status" {
                val query = OppgaveQuery(
                    filtere = listOf(
                        FeltverdiOppgavefilter(
                            område = null,
                            kode = "oppgavestatus",
                            operator = EksternFeltverdiOperator.EQUALS,
                            verdi = listOf(Oppgavestatus.AAPEN.kode)
                        )
                    )
                )
                lagBeskrivelse(query) shouldBe "Åpen"
            }

            "skal beskrive flere statuser" {
                val query = OppgaveQuery(
                    filtere = listOf(
                        FeltverdiOppgavefilter(
                            område = null,
                            kode = "oppgavestatus",
                            operator = EksternFeltverdiOperator.IN,
                            verdi = listOf(Oppgavestatus.AAPEN.kode, Oppgavestatus.VENTER.kode)
                        )
                    )
                )
                lagBeskrivelse(query) shouldBe "Åpen/venter"
            }
        }

        "ytelsestype" - {
            "skal beskrive én ytelsestype" {
                val query = OppgaveQuery(
                    filtere = listOf(
                        FeltverdiOppgavefilter(
                            område = "K9",
                            kode = "ytelsestype",
                            operator = EksternFeltverdiOperator.EQUALS,
                            verdi = listOf("PSB")
                        )
                    )
                )
                lagBeskrivelse(query) shouldBe "Pleiepenger sykt barn"
            }

            "skal beskrive flere ytelsestyper" {
                val query = OppgaveQuery(
                    filtere = listOf(
                        FeltverdiOppgavefilter(
                            område = "K9",
                            kode = "ytelsestype",
                            operator = EksternFeltverdiOperator.IN,
                            verdi = listOf("PSB", "OMP")
                        )
                    )
                )
                lagBeskrivelse(query) shouldBe "Pleiepenger sykt barn/Omsorgspenger"
            }
        }

        "oppgavetype" - {
            "skal beskrive én oppgavetype" {
                val query = OppgaveQuery(
                    filtere = listOf(
                        FeltverdiOppgavefilter(
                            område = null,
                            kode = "oppgavetype",
                            operator = EksternFeltverdiOperator.EQUALS,
                            verdi = listOf("k9sak")
                        )
                    )
                )
                lagBeskrivelse(query) shouldBe "k9sak"
            }

            "skal beskrive flere oppgavetyper" {
                val query = OppgaveQuery(
                    filtere = listOf(
                        FeltverdiOppgavefilter(
                            område = null,
                            kode = "oppgavetype",
                            operator = EksternFeltverdiOperator.IN,
                            verdi = listOf("k9sak", "k9klage")
                        )
                    )
                )
                lagBeskrivelse(query) shouldBe "k9sak/k9klage"
            }
        }

        "liggerHosBeslutter" - {
            "skal beskrive beslutter-oppgaver" {
                val query = OppgaveQuery(
                    filtere = listOf(
                        FeltverdiOppgavefilter(
                            område = "K9",
                            kode = "liggerHosBeslutter",
                            operator = EksternFeltverdiOperator.EQUALS,
                            verdi = listOf("true")
                        )
                    )
                )
                lagBeskrivelse(query) shouldBe "Til beslutter"
            }

            "skal beskrive med ikke når false" {
                val query = OppgaveQuery(
                    filtere = listOf(
                        FeltverdiOppgavefilter(
                            område = "K9",
                            kode = "liggerHosBeslutter",
                            operator = EksternFeltverdiOperator.EQUALS,
                            verdi = listOf("false")
                        )
                    )
                )
                lagBeskrivelse(query) shouldBe "Ikke til beslutter"
            }
        }

        "behandlingTypekode" - {
            "skal beskrive én behandlingstype" {
                val query = OppgaveQuery(
                    filtere = listOf(
                        FeltverdiOppgavefilter(
                            område = "K9",
                            kode = "behandlingTypekode",
                            operator = EksternFeltverdiOperator.EQUALS,
                            verdi = listOf("BT-002")
                        )
                    )
                )
                lagBeskrivelse(query) shouldBe "Førstegangsbehandling"
            }

            "skal beskrive flere behandlingstyper" {
                val query = OppgaveQuery(
                    filtere = listOf(
                        FeltverdiOppgavefilter(
                            område = "K9",
                            kode = "behandlingTypekode",
                            operator = EksternFeltverdiOperator.IN,
                            verdi = listOf("BT-002", "BT-004")
                        )
                    )
                )
                lagBeskrivelse(query) shouldBe "Førstegangsbehandling/Revurdering"
            }
        }

        "kombinasjoner" - {
            "skal kombinere oppgavestatus og ytelsestype" {
                val query = OppgaveQuery(
                    filtere = listOf(
                        FeltverdiOppgavefilter(
                            område = null,
                            kode = "oppgavestatus",
                            operator = EksternFeltverdiOperator.EQUALS,
                            verdi = listOf(Oppgavestatus.AAPEN.kode)
                        ),
                        FeltverdiOppgavefilter(
                            område = "K9",
                            kode = "ytelsestype",
                            operator = EksternFeltverdiOperator.EQUALS,
                            verdi = listOf("PSB")
                        )
                    )
                )
                lagBeskrivelse(query) shouldBe "Åpen, Pleiepenger sykt barn"
            }

            "skal kombinere alle filtertyper" {
                val query = OppgaveQuery(
                    filtere = listOf(
                        FeltverdiOppgavefilter(
                            område = null,
                            kode = "oppgavestatus",
                            operator = EksternFeltverdiOperator.EQUALS,
                            verdi = listOf(Oppgavestatus.AAPEN.kode)
                        ),
                        FeltverdiOppgavefilter(
                            område = "K9",
                            kode = "ytelsestype",
                            operator = EksternFeltverdiOperator.EQUALS,
                            verdi = listOf("PSB")
                        ),
                        FeltverdiOppgavefilter(
                            område = null,
                            kode = "oppgavetype",
                            operator = EksternFeltverdiOperator.EQUALS,
                            verdi = listOf("k9sak")
                        ),
                        FeltverdiOppgavefilter(
                            område = "K9",
                            kode = "liggerHosBeslutter",
                            operator = EksternFeltverdiOperator.EQUALS,
                            verdi = listOf("true")
                        ),
                        FeltverdiOppgavefilter(
                            område = "K9",
                            kode = "behandlingTypekode",
                            operator = EksternFeltverdiOperator.EQUALS,
                            verdi = listOf("BT-004")
                        )
                    )
                )
                lagBeskrivelse(query) shouldBe "Åpen, Pleiepenger sykt barn, k9sak, Til beslutter, Revurdering"
            }
        }
    }
})
