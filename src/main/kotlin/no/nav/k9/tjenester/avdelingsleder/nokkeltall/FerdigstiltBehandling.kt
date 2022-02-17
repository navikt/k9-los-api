package no.nav.k9.tjenester.avdelingsleder.nokkeltall

import no.nav.k9.domene.modell.FagsakYtelseType
import java.time.LocalDate


data class FerdigstiltBehandling(
    override val dato: LocalDate,
    override val ytelseType: FagsakYtelseType? = null,
    override val behandlingType: String? = null,
    val behandlendeEnhet: String,
): HistorikkElement {

    override fun tilMap(): Map<VelgbartHistorikkfelt, Any?> {
        return mapOf(
                VelgbartHistorikkfelt.DATO to dato,
                VelgbartHistorikkfelt.YTELSETYPE to ytelseType,
                VelgbartHistorikkfelt.BEHANDLINGTYPE to behandlingType,
                VelgbartHistorikkfelt.ENHET to behandlendeEnhet
        )
    }

    override fun feltSelector(felt: Set<VelgbartHistorikkfelt>): HistorikkSeleksjonsresultat {
        return tilMap().filter { felt.contains(it.key) }
    }
}