package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos

import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import no.nav.k9.klage.kodeverk.Fagsystem
import no.nav.k9.klage.kodeverk.behandling.*
import no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.klage.kodeverk.behandling.oppgavetillos.EventHendelse
import no.nav.k9.klage.kontrakt.behandling.oppgavetillos.Aksjonspunkttilstand
import no.nav.k9.klage.typer.AktørId
import no.nav.k9.klage.typer.Periode
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.klage.K9KlageEventDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class UtledOppgavestatusTest : FreeSpec({
    "En behandling" - {
        "med status OPPRETTET" - {
            val behandlingstatus = BehandlingStatus.OPPRETTET
            "gir oppgavestatus UAVKLART" {
                KlageEventTilOppgaveMapper.utledOppgavestatus(Testdata.testevent(behandlingstatus, emptyList())) shouldBe Oppgavestatus.UAVKLART
            }
        }
        "med status AVSLUTTET" - {
            val behandlingstatus = BehandlingStatus.AVSLUTTET
            "gir oppgavestatus LUKKET" {
                KlageEventTilOppgaveMapper.utledOppgavestatus(Testdata.testevent(behandlingstatus, emptyList())) shouldBe Oppgavestatus.LUKKET
            }
        }
        "med status UTREDES, FATTER_VEDTAK eller IVERKSETTER_VEDTAK" - {
            withData(
                BehandlingStatus.UTREDES,
                BehandlingStatus.FATTER_VEDTAK,
                BehandlingStatus.IVERKSETTER_VEDTAK,
            ) { behandlingstatus ->
                "som står i behandlingsteg OVERFØRT_NK" - {
                    val event = Testdata.testevent(behandlingstatus, emptyList(), BehandlingStegType.OVERFØRT_NK)
                    "gir oppgavestatus VENTER" {
                        KlageEventTilOppgaveMapper.utledOppgavestatus(event) shouldBe Oppgavestatus.VENTER
                    }
                }
                "og behandlingen har ingen aksjonspunkter" - {
                    val event = Testdata.testevent(behandlingstatus, emptyList())
                    "gir oppgavestatus UAVKLART" {
                        KlageEventTilOppgaveMapper.utledOppgavestatus(event) shouldBe Oppgavestatus.UAVKLART
                    }
                }
                "og behandlingen har manuelt aksjonspunkt" - {
                    val apKode = "5082"
                    "med status OPPRETTET" - {
                        val apTilstand = Testdata.testAksjonspunktTilstand(apKode, AksjonspunktStatus.OPPRETTET)
                        "gir oppgavestatus ÅPEN" {
                            KlageEventTilOppgaveMapper.utledOppgavestatus(Testdata.testevent(behandlingstatus, listOf(apTilstand))) shouldBe Oppgavestatus.AAPEN
                        }
                        "og autopunkt samtidig" - {
                            val autoKode = "7100"
                            "med status OPPRETTET" - {
                                val apTilstand = Testdata.testAksjonspunktTilstand(apKode, AksjonspunktStatus.OPPRETTET)
                                val autoTilstand = Testdata.testAksjonspunktTilstand(autoKode, AksjonspunktStatus.OPPRETTET)
                                "gir oppgavestatus VENTER" {
                                    KlageEventTilOppgaveMapper.utledOppgavestatus(
                                        Testdata.testevent(behandlingstatus, listOf(apTilstand, autoTilstand)),
                                    ) shouldBe Oppgavestatus.VENTER
                                }
                            }
                        }
                    }
                    "med status UTFØRT eller AVBRUTT" - {
                        withData(
                            AksjonspunktStatus.UTFØRT,
                            AksjonspunktStatus.AVBRUTT
                        ) { apStatus ->
                            val apTilstand = Testdata.testAksjonspunktTilstand(apKode, apStatus)
                            "gir oppgavestatus UAVKLART" {
                                KlageEventTilOppgaveMapper.utledOppgavestatus(Testdata.testevent(behandlingstatus, listOf(apTilstand))) shouldBe Oppgavestatus.UAVKLART
                            }
                        }
                    }
                }
                "og behandlingen har autopunkt" - {
                    val apKode = "7100"
                    "med status OPPRETTET" - {
                        val apTilstand = Testdata.testAksjonspunktTilstand(apKode, AksjonspunktStatus.OPPRETTET)
                        "gir oppgavestatus VENTER" {
                            KlageEventTilOppgaveMapper.utledOppgavestatus(Testdata.testevent(behandlingstatus, listOf(apTilstand))) shouldBe Oppgavestatus.VENTER
                        }
                    }
                    "med status UTFØRT eller AVBRUTT" - {
                        withData(
                            AksjonspunktStatus.UTFØRT,
                            AksjonspunktStatus.AVBRUTT
                        ) { apStatus ->
                            val apTilstand = Testdata.testAksjonspunktTilstand(apKode, apStatus)
                            "gir oppgavestatus UAVKLART" {
                                KlageEventTilOppgaveMapper.utledOppgavestatus(Testdata.testevent(behandlingstatus, listOf(apTilstand))) shouldBe Oppgavestatus.UAVKLART
                            }
                        }
                    }
                }
            }
        }
    }
})

object Testdata {
    fun testAksjonspunktTilstand(apKode: String, status: AksjonspunktStatus): Aksjonspunkttilstand {
        return Aksjonspunkttilstand(
            apKode,
            status,
            null,
            null,
            LocalDateTime.now(),
            LocalDateTime.now()
        )
    }

    fun testevent(status: BehandlingStatus, aksjonspunkter: List<Aksjonspunkttilstand>, steg: BehandlingStegType = BehandlingStegType.VURDER_KLAGE_FØRSTEINSTANS): K9KlageEventDto {
        return K9KlageEventDto(
            eksternId = UUID.randomUUID(),
            fagsystem = Fagsystem.K9SAK,
            saksnummer = Random().nextInt(0, 200).toString(),
            aktørId = Random().nextInt(0, 9999999).toString(),
            resultatType = BehandlingResultatType.IKKE_FASTSATT.kode,
            behandlendeEnhet = null,
            behandlingsårsaker = emptyList(),
            opprettetBehandling = LocalDateTime.now(),
            vedtaksdato = LocalDate.now(),
            pleietrengendeAktørId = AktørId(Random().nextLong(0, 9999999)),
            behandlingStatus = status.kode,
            behandlingSteg = steg.kode,
            behandlingTypeKode = BehandlingType.FØRSTEGANGSSØKNAD.kode,
            behandlingstidFrist = null,
            eventHendelse = EventHendelse.BEHANDLINGSKONTROLL_EVENT,
            eventTid = LocalDateTime.now(),
            ytelseTypeKode = FagsakYtelseType.PLEIEPENGER_SYKT_BARN.kode,
            påklagdBehandlingId = null,
            påklagdBehandlingType = null,
            utenlandstilsnitt = null,
            ansvarligBeslutter = "test",
            ansvarligSaksbehandler = "test",
            fagsakPeriode = Periode(LocalDate.now().minusDays(1), LocalDate.now()),
            relatertPartAktørId = AktørId(2L),
            aksjonspunkttilstander = aksjonspunkter,
        )
    }
}