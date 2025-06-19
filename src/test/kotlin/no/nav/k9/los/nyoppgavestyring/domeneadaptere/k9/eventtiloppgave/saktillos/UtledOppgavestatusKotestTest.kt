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

class UtledOppgavestatusKotestTest : FreeSpec({
        "en behandling" - {
            "med status OPPRETTET" - {
                val event = Testdata.testevent(BehandlingStatus.OPPRETTET, emptyList())
                "gir oppgavestatus AUTOMATISK" {
                    EventTilDtoMapper.utledOppgavestatus(event) shouldBe Oppgavestatus.AUTOMATISK
                }
            }
            "med status AVSLUTTET" - {
                val event = Testdata.testevent(BehandlingStatus.AVSLUTTET, emptyList())
                "gir oppgavestatus LUKKET" {
                    EventTilDtoMapper.utledOppgavestatus(event) shouldBe Oppgavestatus.LUKKET
                }
            }
            "med status IVERKSETTER_VEDTAK" - {
                val event = Testdata.testevent(BehandlingStatus.IVERKSETTER_VEDTAK, emptyList())
                "gir oppgavestatus LUKKET" {
                    EventTilDtoMapper.utledOppgavestatus(event) shouldBe Oppgavestatus.LUKKET
                }
            }
            "med status UTREDES" - {
                val behandlingstatus = BehandlingStatus.UTREDES
                "og manuelt aksjonspunkt" - {
                    val aksjonspunktKode = "9001"
                    "som er åpent" - {
                        val aksjonspunktStatus = AksjonspunktStatus.OPPRETTET
                        "gir oppgavestatus AAPEN" {
                            val event = Testdata.testevent(
                                behandlingstatus,
                                listOf(
                                    Testdata.testAksjonspunktTilstand(aksjonspunktKode, aksjonspunktStatus),
                                    Testdata.testAksjonspunktTilstand("5017", AksjonspunktStatus.AVBRUTT),
                                )
                            )
                            EventTilDtoMapper.utledOppgavestatus(event) shouldBe Oppgavestatus.AAPEN
                        }
                    }
                    "som er AVBRUTT" - {
                        val aksjonspunktStatus = AksjonspunktStatus.AVBRUTT
                        "gir oppgavestatus AUTOMATISK" {
                            val event = Testdata.testevent(
                                behandlingstatus,
                                listOf(
                                    Testdata.testAksjonspunktTilstand(aksjonspunktKode, aksjonspunktStatus),
                                    Testdata.testAksjonspunktTilstand("5017", AksjonspunktStatus.AVBRUTT)
                                )
                            )
                            EventTilDtoMapper.utledOppgavestatus(event) shouldBe Oppgavestatus.AUTOMATISK
                        }
                    }
                    "som er UTFØRT" - {
                        val aksjonspunktStatus = AksjonspunktStatus.UTFØRT
                        "gir oppgavestatus AAPEN" {
                            val event = Testdata.testevent(
                                behandlingstatus,
                                listOf(
                                    Testdata.testAksjonspunktTilstand(aksjonspunktKode, aksjonspunktStatus),
                                    Testdata.testAksjonspunktTilstand("5017", AksjonspunktStatus.AVBRUTT)
                                )
                            )
                            EventTilDtoMapper.utledOppgavestatus(event) shouldBe Oppgavestatus.AAPEN
                        }
                    }
                }
                "og automatisk aksjonspunkt" - {
                    val ap1 = Testdata.testAksjonspunktTilstand("7003", AksjonspunktStatus.OPPRETTET)
                    "og uavbrutt manuelt aksjonspunkt" - {
                            withData(
                                Pair(ap1, Testdata.testAksjonspunktTilstand("9001", AksjonspunktStatus.OPPRETTET)),
                                Pair(ap1, Testdata.testAksjonspunktTilstand("9001", AksjonspunktStatus.UTFØRT)),
                            ) { (a, b) ->
                                "gir oppgavestatus VENTER" {
                                    val event = Testdata.testevent(
                                        behandlingstatus,
                                        listOf(a, b)
                                    )
                                    EventTilDtoMapper.utledOppgavestatus(event) shouldBe Oppgavestatus.VENTER
                            }
                        }
                    }
                    "og avbrutt manuelt aksjonspunkt" - {
                            val ap2 = Testdata.testAksjonspunktTilstand("9001", AksjonspunktStatus.AVBRUTT)
                            "gir oppgavestatus AUTOMATISK" {
                                val event = Testdata.testevent(
                                    behandlingstatus,
                                    listOf(ap1, ap2)
                                )
                                EventTilDtoMapper.utledOppgavestatus(event) shouldBe Oppgavestatus.AUTOMATISK
                            }
                        }
                    }
                "og ingen aksjonspunkter" - {
                    "gir oppgavestatus AUTOMATISK" {
                        val event = Testdata.testevent(
                            behandlingstatus,
                            emptyList()
                            )
                        EventTilDtoMapper.utledOppgavestatus(event) shouldBe Oppgavestatus.AUTOMATISK
                    }
                }
            }
            "med status FATTER_VEDTAK" - {
                val behandlingstatus = BehandlingStatus.FATTER_VEDTAK
                "og manuelt aksjonspunkt" - {
                    val aksjonspunktKode = "9001"
                    "som er åpent" - {
                        val aksjonspunktStatus = AksjonspunktStatus.OPPRETTET
                        "gir oppgavestatus AAPEN" {
                            val event = Testdata.testevent(
                                behandlingstatus,
                                listOf(
                                    Testdata.testAksjonspunktTilstand(aksjonspunktKode, aksjonspunktStatus),
                                    Testdata.testAksjonspunktTilstand("5017", AksjonspunktStatus.AVBRUTT),
                                )
                            )
                            EventTilDtoMapper.utledOppgavestatus(event) shouldBe Oppgavestatus.AAPEN
                        }
                    }
                    "som er AVBRUTT" - {
                        val aksjonspunktStatus = AksjonspunktStatus.AVBRUTT
                        "gir oppgavestatus AUTOMATISK" {
                            val event = Testdata.testevent(
                                behandlingstatus,
                                listOf(
                                    Testdata.testAksjonspunktTilstand(aksjonspunktKode, aksjonspunktStatus),
                                    Testdata.testAksjonspunktTilstand("5017", AksjonspunktStatus.AVBRUTT)
                                )
                            )
                            EventTilDtoMapper.utledOppgavestatus(event) shouldBe Oppgavestatus.AUTOMATISK
                        }
                    }
                    "som er UTFØRT" - {
                        val aksjonspunktStatus = AksjonspunktStatus.UTFØRT
                        "gir oppgavestatus AAPEN" {
                            val event = Testdata.testevent(
                                behandlingstatus,
                                listOf(
                                    Testdata.testAksjonspunktTilstand(aksjonspunktKode, aksjonspunktStatus),
                                    Testdata.testAksjonspunktTilstand("5017", AksjonspunktStatus.AVBRUTT)
                                )
                            )
                            EventTilDtoMapper.utledOppgavestatus(event) shouldBe Oppgavestatus.AAPEN
                        }
                    }
                }
                "og automatisk aksjonspunkt" - {
                    val ap1 = Testdata.testAksjonspunktTilstand("7003", AksjonspunktStatus.OPPRETTET)
                    "og uavbrutt manuelt aksjonspunkt" - {
                        withData(
                            Pair(ap1, Testdata.testAksjonspunktTilstand("9001", AksjonspunktStatus.OPPRETTET)),
                            Pair(ap1, Testdata.testAksjonspunktTilstand("9001", AksjonspunktStatus.UTFØRT)),
                        ) { (a, b) ->
                            "gir oppgavestatus VENTER" {
                                val event = Testdata.testevent(
                                    behandlingstatus,
                                    listOf(a, b)
                                )
                                EventTilDtoMapper.utledOppgavestatus(event) shouldBe Oppgavestatus.VENTER
                            }
                        }
                    }
                    "og avbrutt manuelt aksjonspunkt" - {
                        val ap2 = Testdata.testAksjonspunktTilstand("9001", AksjonspunktStatus.AVBRUTT)
                        "gir oppgavestatus AUTOMATISK" {
                            val event = Testdata.testevent(
                                behandlingstatus,
                                listOf(ap1, ap2)
                            )
                            EventTilDtoMapper.utledOppgavestatus(event) shouldBe Oppgavestatus.AUTOMATISK
                        }
                    }
                }
                "og ingen aksjonspunkter" - {
                    "gir oppgavestatus AUTOMATISK" {
                        val event = Testdata.testevent(
                            behandlingstatus,
                            emptyList()
                        )
                        EventTilDtoMapper.utledOppgavestatus(event) shouldBe Oppgavestatus.AUTOMATISK
                    }
                }
            }
        }
    }
)

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
        )
    }
}