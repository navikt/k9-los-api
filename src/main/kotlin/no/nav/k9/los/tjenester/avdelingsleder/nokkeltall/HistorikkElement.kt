package no.nav.k9.los.tjenester.avdelingsleder.nokkeltall

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonValue
import java.time.LocalDate

typealias HistorikkSeleksjonsresultat = Map<VelgbartHistorikkfelt, Any?>

interface HistorikkElement {
    val dato: LocalDate
    val fagsakYtelseType: String?
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
    YTELSETYPE("fagsakYtelseType"),
    FAGSYSTEM("fagsystemType"),
    BEHANDLINGTYPE("behandlingType"),
    SAKSBEHANDLER("saksbehandler");
}

fun Collection<HistorikkElement>.feltSelector(
    vararg felt: VelgbartHistorikkfelt
): List<HistorikkElementAntall> {
    return map { it.feltSelector(felt.toSet()) }
        .groupingBy { it }.eachCount()
        .map { (key, antall) -> HistorikkElementAntall(key, antall = antall) }
}