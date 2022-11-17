package no.nav.k9.los.integrasjon.dto.inntektarbeidytelse

import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.k9.los.integrasjon.dto.kodeverk.KodeDto
import java.time.LocalDate

data class UtsettelsePeriodeDto(
    @JsonProperty("fom")
    val fom: LocalDate,
    @JsonProperty("tom")
    val tom: LocalDate,
    @JsonProperty("utsettelseArsak")
    private val utsettelseÅrsak: KodeDto
)