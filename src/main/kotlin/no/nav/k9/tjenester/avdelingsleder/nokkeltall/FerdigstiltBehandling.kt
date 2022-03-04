package no.nav.k9.tjenester.avdelingsleder.nokkeltall

import java.time.LocalDate

data class FerdigstiltBehandling(
    override val dato: LocalDate,
    override val ytelseType: String? = null,
    override val behandlingType: String? = null,
    override val fagsystemType: String? = null,
    val behandlendeEnhet: String? = null,
): HistorikkElement {

    override fun tilMap(): Map<VelgbartHistorikkfelt, Any?> {
        return mapOf(
                VelgbartHistorikkfelt.DATO to dato,
                VelgbartHistorikkfelt.YTELSETYPE to ytelseType,
                VelgbartHistorikkfelt.BEHANDLINGTYPE to behandlingType,
                VelgbartHistorikkfelt.FAGSYSTEM to fagsystemType,
                VelgbartHistorikkfelt.ENHET to behandlendeEnhet
        )
    }

    override fun feltSelector(felt: Set<VelgbartHistorikkfelt>): HistorikkSeleksjonsresultat {
        return tilMap().filter { felt.contains(it.key) }
    }
}