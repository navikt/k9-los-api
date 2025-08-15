package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos

import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import no.nav.k9.kodeverk.behandling.*
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.kodeverk.behandling.aksjonspunkt.Venteårsak
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHendelse
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventDto
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class UtledOppgavestatusKoTest : FreeSpec({
    "En behandling" - {
        "med status OPPRETTET" - {
            val behandlingstatus = BehandlingStatus.OPPRETTET
            "gir oppgavestatus UAVKLART" {
                EventTilDtoMapper.utledOppgavestatus(Testdata.testevent(behandlingstatus, emptyList())) shouldBe Oppgavestatus.UAVKLART
            }
        }
        "med status AVSLUTTET" - {
            val behandlingstatus = BehandlingStatus.AVSLUTTET
            "gir oppgavestatus LUKKET" {
                EventTilDtoMapper.utledOppgavestatus(Testdata.testevent(behandlingstatus, emptyList())) shouldBe Oppgavestatus.LUKKET
            }
        }
        "med status UTREDES, FATTER_VEDTAK eller IVERKSETTER_VEDTAK" - {
            withData(
                BehandlingStatus.UTREDES,
                BehandlingStatus.FATTER_VEDTAK,
                BehandlingStatus.IVERKSETTER_VEDTAK,
            ) { behandlingstatus ->
                "og ingen aksjonspunkter" - {
                    "gir oppgavestatus UAVKLART" {
                        EventTilDtoMapper.utledOppgavestatus(Testdata.testevent(behandlingstatus, emptyList())) shouldBe Oppgavestatus.UAVKLART
                    }
                }
                "og manuelt aksjonspunkt" - {
                    val apKode = "9001"
                    "med status OPPRETTET" - {
                        val apTilstand = Testdata.testAksjonspunktTilstand(apKode, AksjonspunktStatus.OPPRETTET)
                        "gir oppgavestatus AAPEN" {
                            EventTilDtoMapper.utledOppgavestatus(
                                Testdata.testevent(behandlingstatus, listOf(apTilstand)),
                            ) shouldBe Oppgavestatus.AAPEN
                        }
                    }
                    "med status UTFØRT eller AVBRUTT" - {
                        withData(
                            Testdata.testAksjonspunktTilstand(apKode, AksjonspunktStatus.UTFØRT),
                            Testdata.testAksjonspunktTilstand(apKode, AksjonspunktStatus.AVBRUTT)
                        ) { apTilstand ->
                            "gir oppgavestatus UAVKLART" {
                                EventTilDtoMapper.utledOppgavestatus(
                                    Testdata.testevent(behandlingstatus, listOf(apTilstand)),
                                ) shouldBe Oppgavestatus.UAVKLART
                            }
                        }
                    }
                }
                "og autopunkt" - {
                    val apKode = "9099"
                    "med status OPPRETTET" - {
                        val apTilstand = Testdata.testAksjonspunktTilstand(apKode, AksjonspunktStatus.OPPRETTET)
                        "gir oppgavestatus VENTER" {
                            EventTilDtoMapper.utledOppgavestatus(
                                Testdata.testevent(behandlingstatus, listOf(apTilstand)),
                            ) shouldBe Oppgavestatus.VENTER
                        }
                    }
                    "med status UTFØRT eller AVBRUTT" - {
                        withData(
                            Testdata.testAksjonspunktTilstand(apKode, AksjonspunktStatus.UTFØRT),
                            Testdata.testAksjonspunktTilstand(apKode, AksjonspunktStatus.AVBRUTT)
                        ) { apTilstand ->
                            "gir oppgavestatus UAVKLART" {
                                EventTilDtoMapper.utledOppgavestatus(
                                    Testdata.testevent(behandlingstatus, listOf(apTilstand)),
                                ) shouldBe Oppgavestatus.UAVKLART
                            }
                        }
                    }
                }
            }
        }
    }
})

object Testdata {
    fun testAksjonspunktTilstand(apKode: String, status: AksjonspunktStatus): AksjonspunktTilstandDto {
        return AksjonspunktTilstandDto(
            apKode,
            status,
            Venteårsak.AVV_DOK,
            "Sara Saksbehandler",
            LocalDateTime.now().plusDays(30),
            LocalDateTime.now(),
            LocalDateTime.now(),
        )
    }

    fun testevent(status: BehandlingStatus, aksjonspunkter: List<AksjonspunktTilstandDto>): K9SakEventDto {
        return K9SakEventDto(
            eksternId = UUID.randomUUID(),
            fagsystem = Fagsystem.K9SAK,
            saksnummer = Random().nextInt(0, 200).toString(),
            aktørId = Random().nextInt(0, 9999999).toString(),
            behandlingId = 123L,
            resultatType = BehandlingResultatType.IKKE_FASTSATT.kode,
            behandlendeEnhet = null,
            aksjonspunktTilstander = aksjonspunkter,
            søknadsårsaker = emptyList(),
            behandlingsårsaker = emptyList(),
            ansvarligSaksbehandlerIdent = null,
            ansvarligBeslutterForTotrinn = null,
            ansvarligSaksbehandlerForTotrinn = null,
            opprettetBehandling = LocalDateTime.now(),
            vedtaksdato = LocalDate.now(),
            pleietrengendeAktørId = Random().nextInt(0, 9999999).toString(),
            behandlingStatus = status.kode,
            behandlingSteg = BehandlingStegType.KONTROLLER_FAKTA.kode,
            behandlingTypeKode = BehandlingType.FØRSTEGANGSSØKNAD.kode,
            behandlingstidFrist = null,
            eventHendelse = EventHendelse.BEHANDLINGSKONTROLL_EVENT,
            eventTid = LocalDateTime.now(),
            aksjonspunktKoderMedStatusListe = mutableMapOf(),
            ytelseTypeKode = FagsakYtelseType.PLEIEPENGER_SYKT_BARN.kode,
            eldsteDatoMedEndringFraSøker = LocalDateTime.now(),
            merknader = emptyList()
        )
    }
}