package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.saksbehandling

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.InternalPlatformDsl.toStr
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.Behandlingstilstand
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavetype
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import java.time.LocalDateTime
import java.util.UUID


class AvstemmerTest : FreeSpec({
    "Åpen behandling" - {
        val behandlingUuid = UUID.randomUUID().toString()
        val behandlinger = listOf(Testdata.testBehandlingstilstand(behandlingUuid))
        "og korresponderende åpen oppgave" - {
            val oppgaver = listOf(Testdata.testOppgave(behandlingUuid))
            "skal gi ingen diff" {
                val rapport = Avstemmer.regnUtDiff(behandlinger, oppgaver)
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
                val rapport = Avstemmer.regnUtDiff(behandlinger, oppgaver)
                rapport.forekomsterILosSomManglerIFagsystem shouldBe emptyList()
                rapport.forekomsterIFagsystemSomManglerILos shouldBe emptyList()
                rapport.forekomsterMedUliktInnhold.size shouldBe 1
            }
        }
    }

    "Behandling under utredning" - {
        val behandlingUuid = UUID.randomUUID().toString()
        val behandlinger = listOf(Testdata.testBehandlingstilstand(behandlingUuid, behandlingStatus = BehandlingStatus.UTREDES))
        "og korresponderende oppgave med annen status" - {
            val oppgaver = listOf(Testdata.testOppgave(behandlingUuid, status = Oppgavestatus.AAPEN))
            "skal gi diff for manuell granskning" {
                val rapport = Avstemmer.regnUtDiff(behandlinger, oppgaver)
                rapport.forekomsterILosSomManglerIFagsystem shouldBe emptyList()
                rapport.forekomsterIFagsystemSomManglerILos shouldBe emptyList()
                rapport.forekomsterMedUliktInnhold  shouldBe emptyList()
                rapport.forekomsterSomGranskesManuelt.size shouldBe 1
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
        venteFrist: LocalDateTime = LocalDateTime.now().plusDays(2),
        ): Behandlingstilstand {
        return Behandlingstilstand(
            saksnummer,
            behandlingUuid,
            behandlingStatus,
            ytelseType,
            venteFrist,
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
            kildeområde = "K9",
            felter = emptyList(),
            versjon = 0
        )
    }
}
