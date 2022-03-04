package no.nav.k9.tjenester.avdelingsleder.nokkeltall

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.modell.Fagsystem
import java.time.LocalDate

typealias HistorikkSeleksjonsresultat = Map<VelgbartHistorikkfelt, Any?>

interface HistorikkElement {
    val dato: LocalDate
    val ytelseType: String?
    val fagsystemType: String?
    val behandlingType: String?

    fun tilMap(): HistorikkSeleksjonsresultat
    fun feltSelector(felt: Set<VelgbartHistorikkfelt>): HistorikkSeleksjonsresultat
}


data class HistorikkElementAntall(
    @get:JsonAnyGetter val historikkElement: HistorikkSeleksjonsresultat,
    var antall: Int,
)

enum class VelgbartHistorikkfelt(
    @JsonValue val kode: String
) {
    DATO("dato"),
    ENHET("behandlendeEnhet"),
    YTELSETYPE("ytelseType"),
    FAGSYSTEM("fagsystemType"),
    BEHANDLINGTYPE("behandlingType");
}

fun Collection<HistorikkElement>.feltSelector(
    vararg felt: VelgbartHistorikkfelt
): List<HistorikkElementAntall> {
    return map { it.feltSelector(felt.toSet()) }
        .groupingBy { it }.eachCount()
        .map { (key, antall) -> HistorikkElementAntall(key, antall = antall) }
}