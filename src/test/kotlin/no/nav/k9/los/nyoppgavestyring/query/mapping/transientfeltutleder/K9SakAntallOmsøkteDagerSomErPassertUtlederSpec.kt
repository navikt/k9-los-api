package no.nav.k9.los.nyoppgavestyring.query.mapping.transientfeltutleder

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.AktivOgPartisjonertOppgaveAjourholdTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.NyOppgaveversjon
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveFeltverdiDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EnkelSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator
import org.koin.test.KoinTest
import org.koin.test.get
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class K9SakAntallOmsøkteDagerSomErPassertUtlederSpec : KoinTest, FreeSpec() {

    init {
        "antallDagerFørDato (ren logikk)" - {
            "ingen perioder gir 0 dager" {
                K9SakAntallOmsøkteDagerSomErPassertUtleder.antallDagerFørDato(
                    emptyList(),
                    LocalDate.of(2025, 6, 1)
                ) shouldBe 0L
            }

            "hel periode i fortiden teller alle dager" {
                K9SakAntallOmsøkteDagerSomErPassertUtleder.antallDagerFørDato(
                    listOf("2025-01-01/2025-01-10"),
                    LocalDate.of(2025, 6, 1)
                ) shouldBe 10L
            }

            "hel periode i fremtiden gir 0 dager" {
                K9SakAntallOmsøkteDagerSomErPassertUtleder.antallDagerFørDato(
                    listOf("2025-07-01/2025-07-10"),
                    LocalDate.of(2025, 6, 1)
                ) shouldBe 0L
            }

            "periode som starter i dag teller 0 dager" {
                K9SakAntallOmsøkteDagerSomErPassertUtleder.antallDagerFørDato(
                    listOf("2025-06-01/2025-06-10"),
                    LocalDate.of(2025, 6, 1)
                ) shouldBe 0L
            }

            "periode som slutter i dag teller alle dager unntatt i dag" {
                K9SakAntallOmsøkteDagerSomErPassertUtleder.antallDagerFørDato(
                    listOf("2025-06-01/2025-06-10"),
                    LocalDate.of(2025, 6, 10)
                ) shouldBe 9L
            }

            "periode som krysser nåtid teller bare dager før i dag" {
                K9SakAntallOmsøkteDagerSomErPassertUtleder.antallDagerFørDato(
                    listOf("2025-06-10/2025-06-20"),
                    LocalDate.of(2025, 6, 15)
                ) shouldBe 5L // 10, 11, 12, 13, 14
            }

            "flere perioder summeres" {
                K9SakAntallOmsøkteDagerSomErPassertUtleder.antallDagerFørDato(
                    listOf(
                        "2025-01-01/2025-01-10",  // 10 dager
                        "2025-06-10/2025-06-20",  // 5 dager (10-14)
                        "2025-07-01/2025-07-10"   // 0 dager
                    ),
                    LocalDate.of(2025, 6, 15)
                ) shouldBe 15L
            }

            "enkeltdag i fortiden teller som 1" {
                K9SakAntallOmsøkteDagerSomErPassertUtleder.antallDagerFørDato(
                    listOf("2025-03-15/2025-03-15"),
                    LocalDate.of(2025, 6, 1)
                ) shouldBe 1L
            }

            "enkeltdag i morgen teller som 0" {
                K9SakAntallOmsøkteDagerSomErPassertUtleder.antallDagerFørDato(
                    listOf("2025-06-02/2025-06-02"),
                    LocalDate.of(2025, 6, 1)
                ) shouldBe 0L
            }

            "enkeltdag i går teller som 1" {
                K9SakAntallOmsøkteDagerSomErPassertUtleder.antallDagerFørDato(
                    listOf("2025-06-01/2025-06-01"),
                    LocalDate.of(2025, 6, 2)
                ) shouldBe 1L
            }
        }

        "antallPasserteDagerSql (mot postgres)" - {
            val transactionalManager = get<TransactionalManager>()
            val oppgaveV3Tjeneste = get<OppgaveV3Tjeneste>()
            val oppgaveQueryService = get<OppgaveQueryService>()
            val ajourholdTjeneste = get<AktivOgPartisjonertOppgaveAjourholdTjeneste>()

            "where-filter på antallOmsøkteDagerSomErPassert filtrerer korrekt" {
                val now = LocalDateTime.now()

                // Oppgave med perioder helt i fortiden (10 dager passert)
                val oppgaveMedPassertePerioder = lagOppgaveDto(
                    perioder = listOf("2020-01-01/2020-01-10")
                )
                transactionalManager.transaction { tx ->
                    val oppgave = oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(NyOppgaveversjon(oppgaveMedPassertePerioder), tx)!!
                    ajourholdTjeneste.ajourholdOppgave(oppgave, 0, tx)
                }

                // Oppgave med perioder langt i fremtiden (0 dager passert)
                val oppgaveUtenPassertePerioder = lagOppgaveDto(
                    perioder = listOf("2099-01-01/2099-01-10")
                )
                transactionalManager.transaction { tx ->
                    val oppgave = oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(NyOppgaveversjon(oppgaveUtenPassertePerioder), tx)!!
                    ajourholdTjeneste.ajourholdOppgave(oppgave, 0, tx)
                }

                // Filtrer: antallOmsøkteDagerSomErPassert >= 5
                val query = OppgaveQuery(
                    filtere = listOf(
                        FeltverdiOppgavefilter("K9", "antallOmsøkteDagerSomErPassert", EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS, listOf(5L)),
                    ),
                    select = listOf(
                        EnkelSelectFelt(område = "K9", kode = "antallOmsøkteDagerSomErPassert"),
                    ),
                )

                val resultat = oppgaveQueryService.query(QueryRequest(query), now)
                resultat.size shouldBe 1
                (resultat[0].feltverdier.first { it.kode == "antallOmsøkteDagerSomErPassert" }.verdi as String).toLong() shouldBe 10L
            }

            "select returnerer korrekt antall for periode som krysser nåtid" {
                val iDag = LocalDate.now()
                val fom = iDag.minusDays(5)
                val tom = iDag.plusDays(5)
                val now = LocalDateTime.now()

                val oppgave = lagOppgaveDto(
                    perioder = listOf("$fom/$tom")
                )
                transactionalManager.transaction { tx ->
                    val o = oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(NyOppgaveversjon(oppgave), tx)!!
                    ajourholdTjeneste.ajourholdOppgave(o, 0, tx)
                }

                val query = OppgaveQuery(
                    filtere = listOf(
                        FeltverdiOppgavefilter(null, "oppgavestatus", EksternFeltverdiOperator.EQUALS, listOf("AAPEN")),
                    ),
                    select = listOf(
                        EnkelSelectFelt(område = "K9", kode = "antallOmsøkteDagerSomErPassert"),
                    ),
                )

                val resultat = oppgaveQueryService.query(QueryRequest(query), now)
                resultat.size shouldBe 1
                (resultat[0].feltverdier.first { it.kode == "antallOmsøkteDagerSomErPassert" }.verdi as String).toLong() shouldBe 5L
            }

            "select returnerer 0 for oppgave uten perioder" {
                val now = LocalDateTime.now()

                val oppgave = lagOppgaveDto(perioder = emptyList())
                transactionalManager.transaction { tx ->
                    val o = oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(NyOppgaveversjon(oppgave), tx)!!
                    ajourholdTjeneste.ajourholdOppgave(o, 0, tx)
                }

                val query = OppgaveQuery(
                    filtere = listOf(
                        FeltverdiOppgavefilter(null, "oppgavestatus", EksternFeltverdiOperator.EQUALS, listOf("AAPEN")),
                    ),
                    select = listOf(
                        EnkelSelectFelt(område = "K9", kode = "antallOmsøkteDagerSomErPassert"),
                    ),
                )

                val resultat = oppgaveQueryService.query(QueryRequest(query), now)
                resultat.size shouldBe 1
                (resultat[0].feltverdier.first { it.kode == "antallOmsøkteDagerSomErPassert" }.verdi as String).toLong() shouldBe 0L
            }

            "flere perioder summeres korrekt i SQL" {
                val now = LocalDateTime.now()

                val oppgave = lagOppgaveDto(
                    perioder = listOf(
                        "2020-01-01/2020-01-10",  // 10 dager
                        "2020-03-01/2020-03-05",  // 5 dager
                    )
                )
                transactionalManager.transaction { tx ->
                    val o = oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(NyOppgaveversjon(oppgave), tx)!!
                    ajourholdTjeneste.ajourholdOppgave(o, 0, tx)
                }

                val query = OppgaveQuery(
                    filtere = listOf(
                        FeltverdiOppgavefilter(null, "oppgavestatus", EksternFeltverdiOperator.EQUALS, listOf("AAPEN")),
                    ),
                    select = listOf(
                        EnkelSelectFelt(område = "K9", kode = "antallOmsøkteDagerSomErPassert"),
                    ),
                )

                val resultat = oppgaveQueryService.query(QueryRequest(query), now)
                resultat.size shouldBe 1
                (resultat[0].feltverdier.first { it.kode == "antallOmsøkteDagerSomErPassert" }.verdi as String).toLong() shouldBe 15L
            }
        }
    }

    private fun lagOppgaveDto(perioder: List<String>): OppgaveDto {
        val feltverdier = mutableListOf(
            OppgaveFeltverdiDto(nøkkel = "behandlingUuid", verdi = UUID.randomUUID().toString()),
            OppgaveFeltverdiDto(nøkkel = "aktorId", verdi = "1234567890123"),
            OppgaveFeltverdiDto(nøkkel = "fagsystem", verdi = "K9SAK"),
            OppgaveFeltverdiDto(nøkkel = "saksnummer", verdi = "ABC123"),
            OppgaveFeltverdiDto(nøkkel = "resultattype", verdi = "IKKE_FASTSATT"),
            OppgaveFeltverdiDto(nøkkel = "ytelsestype", verdi = "PSB"),
            OppgaveFeltverdiDto(nøkkel = "behandlingsstatus", verdi = "UTRED"),
            OppgaveFeltverdiDto(nøkkel = "behandlingTypekode", verdi = "BT-002"),
            OppgaveFeltverdiDto(nøkkel = "totrinnskontroll", verdi = "false"),
            OppgaveFeltverdiDto(nøkkel = "avventerSøker", verdi = "false"),
            OppgaveFeltverdiDto(nøkkel = "avventerArbeidsgiver", verdi = "false"),
            OppgaveFeltverdiDto(nøkkel = "avventerSaksbehandler", verdi = "true"),
            OppgaveFeltverdiDto(nøkkel = "avventerTekniskFeil", verdi = "false"),
            OppgaveFeltverdiDto(nøkkel = "avventerAnnet", verdi = "false"),
            OppgaveFeltverdiDto(nøkkel = "avventerAnnetIkkeSaksbehandlingstid", verdi = "false"),
            OppgaveFeltverdiDto(nøkkel = "helautomatiskBehandlet", verdi = "false"),
            OppgaveFeltverdiDto(nøkkel = "utenlandstilsnitt", verdi = "false"),
            OppgaveFeltverdiDto(nøkkel = "direkteutbetaling", verdi = "false"),
        )

        if (perioder.isNotEmpty()) {
            perioder.forEach { periode ->
                feltverdier.add(OppgaveFeltverdiDto(nøkkel = "relevanteSøknadsperioder", verdi = periode))
            }
        } else {
            feltverdier.add(OppgaveFeltverdiDto(nøkkel = "relevanteSøknadsperioder", verdi = null))
        }

        return OppgaveDto(
            eksternId = UUID.randomUUID().toString(),
            eksternVersjon = LocalDateTime.now().toString(),
            område = "K9",
            kildeområde = "k9-sak-til-los",
            type = "k9sak",
            status = "AAPEN",
            endretTidspunkt = LocalDateTime.now(),
            reservasjonsnøkkel = UUID.randomUUID().toString(),
            feltverdier = feltverdier,
        )
    }
}







