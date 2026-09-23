package no.nav.k9.los.domeneadaptere.eventtiloppgave.akt

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsNone
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.kodeverk.AktBehandlendeEnhet
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.kodeverk.AktFagsystem
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.FeltdefinisjonTjeneste
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.FeltdefinisjonerDto
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.KodeverkDto
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.Synlighet
import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRepository
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavetypeTjeneste
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavetyperDto
import no.nav.ung.kodeverk.behandling.BehandlingResultatType
import no.nav.ung.kodeverk.behandling.BehandlingStatus
import no.nav.ung.kodeverk.behandling.BehandlingStegType
import no.nav.ung.kodeverk.behandling.BehandlingType
import no.nav.ung.kodeverk.behandling.BehandlingÅrsakType
import no.nav.ung.kodeverk.behandling.FagsakYtelseType
import no.nav.ung.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.ung.kodeverk.behandling.aksjonspunkt.Venteårsak
import org.junit.jupiter.api.Test

class OmrådesetupTest {

    private class Kjøring(
        val kodeverk: List<KodeverkDto>
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

        Områdesetup(områdeRepository, feltdefinisjonTjeneste, oppgavetypeTjeneste).setup()

        return Kjøring(
            kodeverk = kodeverkSlot.captured
        )
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


