package no.nav.k9.los.nyoppgavestyring.datainnlasting.feltdefinisjon

import no.nav.k9.los.nyoppgavestyring.datainnlasting.omraade.Område

class KodeverkForOmråde(
    val område: Område,
    val kodeverk: List<Kodeverk>,
) {
    fun hentKodeverk(eksternId: String) : Kodeverk {
        return kodeverk.firstOrNull { kodeverk ->
            kodeverk.eksternId == eksternId
        } ?: throw IllegalArgumentException("Fant ikke omsøkt kodeverk: $eksternId for område: ${område.eksternId}")
    }

    fun hentKodeverk(kodeverkId: Long) : Kodeverk {
        return kodeverk.firstOrNull { kodeverk -> kodeverk.id == kodeverkId }
            ?: throw IllegalArgumentException("Fant ikke omsøkt kodeverk med Id: $kodeverkId for område: ${område.eksternId}")
    }
}