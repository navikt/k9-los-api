package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.punsjtillos

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.sak.typer.JournalpostId
import java.time.LocalDateTime
import java.util.*

class UtledOppgavestatusTest : FreeSpec( {
    "Base case" - {
        "skal gi oppgavestatus ÅPEN" {
            PunsjEventTilOppgaveMapper.utledOppgavestatus(
                Testdata.testevent(Oppgavestatus.AAPEN, sendtInn = false, mapOf(Pair("PUNSJ", "Test")).toMutableMap())
            ) shouldBe Oppgavestatus.AAPEN
        }
    }
    "Los-event som" - {
        "er sendt inn" - {
            val event = Testdata.testevent(Oppgavestatus.AAPEN, sendtInn = true, emptyMap<String, String>().toMutableMap())
            "skal gi oppgavestatus LUKKET" {
                PunsjEventTilOppgaveMapper.utledOppgavestatus(event) shouldBe Oppgavestatus.LUKKET
            }
        }
        "har status LUKKET" - {
            val event = Testdata.testevent(Oppgavestatus.LUKKET, sendtInn = false, emptyMap<String, String>().toMutableMap())
            "skal gi oppgavestatus LUKKET" {
                PunsjEventTilOppgaveMapper.utledOppgavestatus(event) shouldBe Oppgavestatus.LUKKET
            }
        }
        "har tom aksjonspunktliste" - {
            val event = Testdata.testevent(Oppgavestatus.AAPEN, sendtInn = false, emptyMap<String, String>().toMutableMap())
            "skal gi oppgavestatus LUKKET" {
                PunsjEventTilOppgaveMapper.utledOppgavestatus(event) shouldBe Oppgavestatus.LUKKET
            }
        }
        "har aksjonspunkt med kode MER_INFORMASJON" - {
            val event = Testdata.testevent(
                Oppgavestatus.AAPEN,
                sendtInn = false,
                mapOf(Pair("MER_INFORMASJON",AksjonspunktStatus.OPPRETTET.kode)).toMutableMap())
            "skal gi oppgavestatus VENTER" {
                PunsjEventTilOppgaveMapper.utledOppgavestatus(event) shouldBe Oppgavestatus.VENTER
            }
        }
    }
})

object Testdata {
    fun testevent(status: Oppgavestatus?, sendtInn: Boolean?, apStatus: MutableMap<String, String>) : K9PunsjEventDto {
        return K9PunsjEventDto(
            eksternId = UUID.randomUUID(),
            journalpostId = JournalpostId(123L),
            eventTid = LocalDateTime.now(),
            status = status,
            aktørId = null,
            aksjonspunktKoderMedStatusListe = apStatus,
            sendtInn = sendtInn,
        )
    }
}