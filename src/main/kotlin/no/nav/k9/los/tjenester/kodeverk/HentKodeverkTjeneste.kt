package no.nav.k9.los.tjenester.kodeverk

import no.nav.k9.los.Configuration
import no.nav.k9.los.domene.lager.oppgave.Kodeverdi
import no.nav.k9.los.domene.modell.*
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.Venteårsak

class HentKodeverkTjeneste(
    private val configuration: Configuration,
) {
    fun hentGruppertKodeliste(): MutableMap<String, Collection<out Kodeverdi>> {
        return KODEVERK_ENUM
    }

    private var KODEVERK_ENUM = makeMap()

    private fun makeMap(): MutableMap<String, Collection<out Kodeverdi>> {
        val koder = mutableMapOf<String, Collection<out Kodeverdi>>()

        koder[BehandlingType::class.java.simpleName] = BehandlingType.values().asList()
        koder[FagsakYtelseType::class.java.simpleName] = FagsakYtelseType.values().asList()
        koder[KøSortering::class.java.simpleName] = KøSortering.values().asList()
        koder[FagsakStatus::class.java.simpleName] = FagsakStatus.values().asList()

        koder[AndreKriterierType::class.java.simpleName] = AndreKriterierType.values().asList()
        koder[BehandlingStatus::class.java.simpleName] = BehandlingStatus.values().asList()
        koder[Venteårsak::class.java.simpleName] = Venteårsak.values().asList()
        koder[KøKriterierType::class.java.simpleName] = KøKriterierType.values().asList()
            .filterNot { it == KøKriterierType.BEHANDLINGTYPE } // ikke i bruk foreløpig
        koder[MerknadType::class.java.simpleName] = MerknadType.values().asList()
            .filterNot { it == MerknadType.VANSKELIG } // ikke støttet foreløpig
        koder[OppgaveKode::class.java.simpleName] = OppgaveKode.values().asList()
        return koder
    }
}
