package no.nav.k9.tjenester.kodeverk

import no.nav.k9.domene.lager.oppgave.Kodeverdi
import no.nav.k9.domene.modell.*

class HentKodeverkTjeneste  {
     fun hentGruppertKodeliste(): MutableMap<String, Collection<out Kodeverdi>> {
        return KODEVERK_ENUM
    }

    private var KODEVERK_ENUM = makeMap()

    fun makeMap(): MutableMap<String, Collection<out Kodeverdi>> {
        val koder = mutableMapOf<String, Collection<out Kodeverdi>>()

        koder[BehandlingType::class.java.simpleName] = BehandlingType.values().asList()
        koder[FagsakYtelseType::class.java.simpleName] = FagsakYtelseType.values().asList()
        koder[KøSortering::class.java.simpleName] = KøSortering.values().asList()
        koder[FagsakStatus::class.java.simpleName] = FagsakStatus.values().asList()
        koder[AndreKriterierType::class.java.simpleName] = AndreKriterierType.values().asList()
        koder[BehandlingStatus::class.java.simpleName] = BehandlingStatus.values().asList()
        return koder
    }
}
