package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.saksbehandling

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.Behandlingstilstand
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavetype
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgavefelt
import java.time.LocalDateTime
import java.util.UUID


class AvstemmerSpec : FreeSpec({
    "Åpen behandling" - {
        val behandlingUuid = UUID.randomUUID().toString()
        val behandlinger = listOf(Testdata.testBehandlingstilstand(behandlingUuid))
        "og korresponderende åpen oppgave" - {
            val oppgaver = listOf(Testdata.testOppgave(behandlingUuid))
            "skal gi ingen diff" {
                val rapport = SakAvstemmer.regnUtDiff(Fagsystem.K9SAK, behandlinger, oppgaver)
                rapport.forekomsterILosSomManglerIFagsystem shouldBe emptyList()
                rapport.forekomsterIFagsystemSomManglerILos shouldBe emptyList()
            }
        }
    }
    "Avsluttet behandling" - {
        val behandlingUuid = UUID.randomUUID().toString()
        val behandlinger = listOf(Testdata.testBehandlingstilstand(behandlingUuid, behandlingStatus = BehandlingStatus.AVSLUTTET))
        "og korresponderende oppgave med annen status" - {
            val oppgaver = listOf(Testdata.testOppgave(behandlingUuid, status = Oppgavestatus.AAPEN))
            "skal gi diff på ulikt innhold i oppgaven" {
                val rapport = SakAvstemmer.regnUtDiff(Fagsystem.K9SAK, behandlinger, oppgaver)
                rapport.forekomsterILosSomManglerIFagsystem shouldBe emptyList()
                rapport.forekomsterIFagsystemSomManglerILos shouldBe emptyList()
                rapport.forekomsterMedUliktInnhold.size shouldBe 1
            }
        }
    }

    "Behandling under utredning" - {
        val behandlingUuid = UUID.randomUUID().toString()
        val behandlingstatus = BehandlingStatus.UTREDES
        val behandlinger = listOf(Testdata.testBehandlingstilstand(behandlingUuid, behandlingStatus = behandlingstatus))
        "og autopunkt" - { //autopunkt == true når ventefrist er satt
            "og korresponderende oppgave på vent" - {
                val oppgaver = listOf(Testdata.testOppgave(behandlingUuid, status = Oppgavestatus.VENTER))
                "skal gi ingen diff" {
                    val rapport = SakAvstemmer.regnUtDiff(Fagsystem.K9SAK, behandlinger, oppgaver)
                    rapport.forekomsterILosSomManglerIFagsystem shouldBe emptyList()
                    rapport.forekomsterIFagsystemSomManglerILos shouldBe emptyList()
                    rapport.forekomsterMedUliktInnhold  shouldBe emptyList()
                    rapport.forekomsterSomGranskesManuelt shouldBe emptyList()
                }
            }
            "og korresponderende oppgave annen status enn på vent" - {
                val oppgaver = listOf(Testdata.testOppgave(behandlingUuid, status = Oppgavestatus.AAPEN))
                "skal gi diff på ulikt innhold i oppgaven" {
                    val rapport = SakAvstemmer.regnUtDiff(Fagsystem.K9SAK, behandlinger, oppgaver)
                    rapport.forekomsterMedUliktInnhold.size shouldBe 1
                }
            }
        }
        "og manuelt aksjonspunkt" - { //manuelt aksjonspunkt == false når ventefrist ikke er satt
            val behandlingerUtenVenteFrist = listOf(
                Testdata.testBehandlingstilstand(
                    behandlingUuid,
                    behandlingStatus = behandlingstatus,
                    harManueltAP = true,
                    venteFrist = null
                )
            )
            "og korresponderende åpen oppgave" - {
                val oppgaver = listOf(Testdata.testOppgave(behandlingUuid, status = Oppgavestatus.AAPEN))
                "skal gi ingen diff" {
                    val rapport = SakAvstemmer.regnUtDiff(Fagsystem.K9SAK, behandlingerUtenVenteFrist, oppgaver)
                    rapport.forekomsterILosSomManglerIFagsystem shouldBe emptyList()
                    rapport.forekomsterIFagsystemSomManglerILos shouldBe emptyList()
                    rapport.forekomsterMedUliktInnhold  shouldBe emptyList()
                    rapport.forekomsterSomGranskesManuelt shouldBe emptyList()
                }
            }
            "og korresponderende oppgave med annen status" - {
                val oppgaver = listOf(Testdata.testOppgave(behandlingUuid, status = Oppgavestatus.VENTER))
                "skal gi diff på ulikt innhold i oppgaven" {
                    val rapport = SakAvstemmer.regnUtDiff(Fagsystem.K9SAK, behandlingerUtenVenteFrist, oppgaver)
                    rapport.forekomsterILosSomManglerIFagsystem shouldBe emptyList()
                    rapport.forekomsterIFagsystemSomManglerILos shouldBe emptyList()
                    rapport.forekomsterMedUliktInnhold.size shouldBe 1
                }
            }
        }
    }
})

object Testdata {
    fun testBehandlingstilstand(
        behandlingUuid: String = UUID.randomUUID().toString(),
        saksnummer: String = System.nanoTime().toString() + "",
        behandlingStatus: BehandlingStatus = BehandlingStatus.UTREDES,
        ytelseType: FagsakYtelseType = FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
        venteFrist: LocalDateTime? = LocalDateTime.now().plusDays(2),
        harManueltAP: Boolean = true,
        ): Behandlingstilstand {
        return Behandlingstilstand(
            saksnummer,
            behandlingUuid,
            behandlingStatus,
            ytelseType,
            venteFrist,
            harManueltAP
        )
    }

    fun testOppgave(
        eksternId: String = UUID.randomUUID().toString(),
        status: Oppgavestatus = Oppgavestatus.AAPEN,
    ): Oppgave {
        return Oppgave(
            eksternId = eksternId.toString(),
            eksternVersjon = "versjon",
            reservasjonsnøkkel = "reservasjonsnøkkel",
            oppgavetype = Oppgavetype(
                eksternId = "123",
                område = Område(eksternId = "test"),
                definisjonskilde = "junit",
                oppgavebehandlingsUrlTemplate = "\${baseUrl}/fagsak/\${K9.saksnummer}/behandling/\${K9.behandlingUuid}?fakta=default&punkt=default",
                oppgavefelter = setOf()
            ),
            status = status.toString(),
            endretTidspunkt = LocalDateTime.now(),
            felter = listOf(
                Oppgavefelt(
                    område = "K9",
                    eksternId = "saksnummer",
                    verdi = "saksnummer123",
                    listetype = false,
                    påkrevd = true,
                    verdiBigInt = null,
                ),
                Oppgavefelt(
                    område = "K9",
                    eksternId = "ytelsestype",
                    verdi = "ytelse",
                    listetype = false,
                    påkrevd = true,
                    verdiBigInt = null,
                )
            ),
        )
    }
}
