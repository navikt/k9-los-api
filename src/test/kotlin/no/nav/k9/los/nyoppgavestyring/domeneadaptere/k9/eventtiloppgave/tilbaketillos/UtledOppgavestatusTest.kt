package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.tilbaketillos

import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import no.nav.k9.klage.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.klage.kodeverk.behandling.BehandlingStegType
import no.nav.k9.klage.kodeverk.behandling.BehandlingType
import no.nav.k9.klage.kodeverk.behandling.FagsakYtelseType
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHendelse
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeEventDto
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import java.time.LocalDateTime
import java.util.*


class UtledOppgavestatusTest: FreeSpec({
    "En behandling" - {
        "med status OPPRETTET" - {
            val behandlingstatus = BehandlingStatus.OPPRETTET
            "gir status UAVKLART" {
                EventTilDtoMapper.utledOppgavestatus(Testdata.testevent(behandlingstatus, mutableMapOf())) shouldBe Oppgavestatus.UAVKLART
            }
        }
        "med status AVSLUTTET" - {
            val behandlingstatus = BehandlingStatus.AVSLUTTET
            "gir status LUKKET" {
                EventTilDtoMapper.utledOppgavestatus(Testdata.testevent(behandlingstatus, mutableMapOf())) shouldBe Oppgavestatus.LUKKET
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
                        EventTilDtoMapper.utledOppgavestatus(Testdata.testevent(behandlingstatus, mutableMapOf())) shouldBe Oppgavestatus.UAVKLART
                    }
                }
                "og manuelt aksjonspunkt" - {
                    val apKode = "5002"
                    "med status OPPRETTET" - {
                        val apTilstand = Testdata.testAksjonspunktTilstand(apKode, AksjonspunktStatus.OPPRETTET)
                        "gir oppgavestatus AAPEN" {
                            EventTilDtoMapper.utledOppgavestatus(
                                Testdata.testevent(behandlingstatus, apTilstand),
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
                                    Testdata.testevent(behandlingstatus, apTilstand),
                                ) shouldBe Oppgavestatus.UAVKLART
                            }
                        }
                    }
                }
                "og autopunkt" - {
                    val apKode = "7001"
                    "med status OPPRETTET" - {
                        val apTilstand = Testdata.testAksjonspunktTilstand(apKode, AksjonspunktStatus.OPPRETTET)
                        "gir oppgavestatus VENTER" {
                            EventTilDtoMapper.utledOppgavestatus(
                                Testdata.testevent(behandlingstatus, apTilstand),
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
                                    Testdata.testevent(behandlingstatus, apTilstand),
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
    fun testAksjonspunktTilstand(apKode: String, status: AksjonspunktStatus): MutableMap<String, String> {
        return mutableMapOf(Pair(apKode, status.kode))
    }

    fun testevent(status: BehandlingStatus, aksjonspunkter: MutableMap<String, String>, steg: BehandlingStegType = BehandlingStegType.VURDER_KLAGE_FØRSTEINSTANS): K9TilbakeEventDto {
        return K9TilbakeEventDto(
            eksternId = UUID.randomUUID(),
            fagsystem = "test",
            saksnummer = Random().nextInt(0, 200).toString(),
            aktørId = Random().nextInt(0, 9999999).toString(),
            resultatType = BehandlingResultatType.IKKE_FASTSATT.kode,
            behandlendeEnhet = null,
            opprettetBehandling = LocalDateTime.now(),
            behandlingStatus = status.kode,
            behandlingSteg = steg.kode,
            behandlingTypeKode = BehandlingType.FØRSTEGANGSSØKNAD.kode,
            behandlingstidFrist = null,
            eventHendelse = EventHendelse.BEHANDLINGSKONTROLL_EVENT,
            eventTid = LocalDateTime.now(),
            ytelseTypeKode = FagsakYtelseType.PLEIEPENGER_SYKT_BARN.kode,
            behandlingId = 123L,
            aksjonspunktKoderMedStatusListe = aksjonspunkter,
        )
    }
}