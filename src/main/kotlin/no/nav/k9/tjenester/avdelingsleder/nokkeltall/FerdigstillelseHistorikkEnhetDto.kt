package no.nav.k9.tjenester.avdelingsleder.nokkeltall

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.k9.domene.modell.BehandlingType
import no.nav.k9.domene.modell.FagsakYtelseType
import java.time.LocalDate

@JsonInclude(JsonInclude.Include.NON_NULL)
data class FerdigstillelseHistorikkEnhetDto(
    val dato: LocalDate,
    var behandlendeEnhet: List<AntallPrEnhet>? = null,
    var ytelseType: List<AntallPrYtelsetype>? = null
) {

    data class AntallPrEnhet(
        val enhet: String,
        val antall: Int
    )

    data class AntallPrYtelsetype(
        private val fagsakYtelseType: FagsakYtelseType,
        private val behandlingType: BehandlingType,
        val antall: Int
    ) {

        @JsonProperty("fagsakYtelseType")
        fun fagsakYtelseType(): String {
            return fagsakYtelseType.kode
        }

        @JsonProperty("behandlingType")
        fun behandlingType(): String {
            return behandlingType.kode
        }
    }
}


fun FerdigstillelseHistorikkEnhet.tilDto(): FerdigstillelseHistorikkEnhetDto {
    return FerdigstillelseHistorikkEnhetDto(
        dato = this.dato,
        behandlendeEnhet = this.behandlendeEnhet?.map { it.tilDto() },
        ytelseType = this.ytelseType?.map { it.tilDto() }
    )
}

fun FerdigstillelseHistorikkEnhet.AntallPrEnhet.tilDto(): FerdigstillelseHistorikkEnhetDto.AntallPrEnhet {
    return FerdigstillelseHistorikkEnhetDto.AntallPrEnhet(enhet = enhet, antall = antall)
}

fun FerdigstillelseHistorikkEnhet.AntallPrYtelsetype.tilDto(): FerdigstillelseHistorikkEnhetDto.AntallPrYtelsetype {
    return FerdigstillelseHistorikkEnhetDto.AntallPrYtelsetype(fagsakYtelseType = fagsakYtelseType, behandlingType = behandlingType, antall = antall)
}