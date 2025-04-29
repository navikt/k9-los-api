package no.nav.k9.los.nyoppgavestyring.kodeverk

class HentKodeverkTjeneste {
    fun hentGruppertKodeliste(): MutableMap<String, Collection<Kodeverdi>> {
        val koder = mutableMapOf<String, Collection<Kodeverdi>>()
        koder[BehandlingType::class.java.simpleName] = BehandlingType.entries
        koder[FagsakYtelseType::class.java.simpleName] = FagsakYtelseType.entries
        koder[KøSortering::class.java.simpleName] = KøSortering.entries
        koder[FagsakStatus::class.java.simpleName] = FagsakStatus.entries
        koder[AndreKriterierType::class.java.simpleName] = AndreKriterierType.entries
        koder[BehandlingStatus::class.java.simpleName] = BehandlingStatus.entries
        koder[Venteårsak::class.java.simpleName] = Venteårsak.entries
        koder[KøKriterierType::class.java.simpleName] = KøKriterierType.entries
            .filterNot { it == KøKriterierType.BEHANDLINGTYPE } // ikke i bruk foreløpig
        koder[MerknadType::class.java.simpleName] = MerknadType.entries
            .filterNot { it == MerknadType.VANSKELIG } // ikke støttet foreløpig
        koder[OppgaveKode::class.java.simpleName] = OppgaveKode.entries
        return koder
    }
}
