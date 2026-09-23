package no.nav.k9.los.domeneadaptere.eventtiloppgave.akt

import assertk.assertThat
import assertk.assertions.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.kodeverk.AktBehandlendeEnhet
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.kodeverk.AktFagsystem
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.oppgavedefinisjon.AktivitetspengerFeltIder
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.FeltdefinisjonTjeneste
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.FeltdefinisjonerDto
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.KodeverkDto
import no.nav.k9.los.oppgavedefinisjon.omraade.Område
import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRepository
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.Oppgavetype
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavetypeTjeneste
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavetyperDto
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.oppgaveuthenting.Oppgavefelt
import no.nav.ung.kodeverk.behandling.*
import no.nav.ung.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.ung.kodeverk.behandling.aksjonspunkt.Venteårsak
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class OmrådesetupTest {

    private class Kjøring(
        val kodeverk: List<KodeverkDto>,
        val oppgavetyper: OppgavetyperDto,
    )

    private fun kjørSetup(): Kjøring {
        val områdeRepository = mockk<OmrådeRepository>(relaxed = true)
        val feltdefinisjonTjeneste = mockk<FeltdefinisjonTjeneste>(relaxed = true)
        val oppgavetypeTjeneste = mockk<OppgavetypeTjeneste>(relaxed = true)

        val kodeverkSlot = slot<List<KodeverkDto>>()
        val feltdefinisjonerSlot = slot<FeltdefinisjonerDto>()
        val oppgavetyperSlot = slot<OppgavetyperDto>()

        every { feltdefinisjonTjeneste.oppdater(capture(kodeverkSlot)) } returns Unit
        every { feltdefinisjonTjeneste.oppdater(capture(feltdefinisjonerSlot)) } returns Unit
        every { oppgavetypeTjeneste.oppdater(capture(oppgavetyperSlot)) } returns Unit

        Områdesetup(områdeRepository, feltdefinisjonTjeneste, oppgavetypeTjeneste, FRONTEND_URL).setup()

        return Kjøring(
            kodeverk = kodeverkSlot.captured,
            oppgavetyper = oppgavetyperSlot.captured,
        )
    }

    @Test
    fun `behandlingsurl bygges fra frontend-url og saksnummer i aktivitetspenger-området`() {
        val oppgavetyper = kjørSetup().oppgavetyper.oppgavetyper
        assertThat(oppgavetyper).isNotEmpty()

        oppgavetyper.forEach { dto ->
            val oppgave = Oppgave(
                eksternId = "1",
                eksternVersjon = "1",
                reservasjonsnøkkel = "r",
                oppgavetype = Oppgavetype(
                    eksternId = dto.id,
                    område = Område(eksternId = Områder.AKTIVITETSPENGER.eksternId),
                    oppgavebehandlingsUrlTemplate = dto.oppgavebehandlingsUrlTemplate,
                    oppgavefelter = emptySet(),
                ),
                status = Oppgavestatus.AAPEN,
                endretTidspunkt = LocalDateTime.now(),
                felter = listOf(
                    Oppgavefelt(
                        eksternId = AktivitetspengerFeltIder.Sak.SAKSNUMMER,
                        område = Områder.AKTIVITETSPENGER,
                        listetype = false,
                        påkrevd = true,
                        verdi = "AKT12345",
                        verdiBigInt = null,
                    )
                ),
            )

            assertThat(oppgave.getOppgaveBehandlingsurl()).isEqualTo("$FRONTEND_URL/fagsak/AKT12345/")
        }
    }

    private companion object {
        const val FRONTEND_URL = "http://ungsak.frontend"
    }

    private fun List<KodeverkDto>.medEksternId(eksternId: String) =
        single { it.eksternId == eksternId }

    @Test
    fun `kodeverk uten verdier finnes ikke`() {
        val kodeverk = kjørSetup().kodeverk

        assertThat(kodeverk.filter { it.verdier.isEmpty() }).isEmpty()
    }

    @Test
    fun `uttømmende settes kun for lukkede kodeverk`() {
        val kodeverk = kjørSetup().kodeverk

        listOf(
            BehandlingType::class.java.simpleName,
            BehandlingStatus::class.java.simpleName,
            BehandlingÅrsakType::class.java.simpleName,
            AktFagsystem::class.java.simpleName,
            BehandlingResultatType::class.java.simpleName,
            FagsakYtelseType::class.java.simpleName,
        ).forEach { assertThat(kodeverk.medEksternId(it).uttømmende).isTrue() }

        listOf(
            BehandlingStegType::class.java.simpleName,
            AktBehandlendeEnhet::class.java.simpleName,
            AksjonspunktDefinisjon::class.java.simpleName,
            Venteårsak::class.java.simpleName,
        ).forEach { assertThat(kodeverk.medEksternId(it).uttømmende).isFalse() }
    }
}


