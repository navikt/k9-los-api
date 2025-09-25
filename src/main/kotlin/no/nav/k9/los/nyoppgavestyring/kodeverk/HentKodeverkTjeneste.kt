package no.nav.k9.los.nyoppgavestyring.kodeverk

class HentKodeverkTjeneste {
    fun hentGruppertKodeliste(): MutableMap<String, Collection<Kodeverdi>> {
        val koder = mutableMapOf<String, Collection<Kodeverdi>>()
        koder[BehandlingType::class.java.simpleName] = BehandlingType.entries
        koder[FagsakYtelseType::class.java.simpleName] = FagsakYtelseType.entries
        koder[FagsakStatus::class.java.simpleName] = FagsakStatus.entries
        koder[AndreKriterierType::class.java.simpleName] = AndreKriterierType.entries
        koder[BehandlingStatus::class.java.simpleName] = BehandlingStatus.entries
        koder[Venteårsak::class.java.simpleName] = Venteårsak.entries
        koder[OppgaveKode::class.java.simpleName] = OppgaveKode.entries
        return koder
    }
}
