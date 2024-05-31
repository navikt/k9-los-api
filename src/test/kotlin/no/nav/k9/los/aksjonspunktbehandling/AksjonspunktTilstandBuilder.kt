package no.nav.k9.los.aksjonspunktbehandling

import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.kodeverk.behandling.aksjonspunkt.Venteårsak
import no.nav.k9.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto
import java.time.LocalDateTime

data class AksjonspunktTilstandBuilder(
    val kode: String,
    var status: AksjonspunktStatus = AksjonspunktStatus.OPPRETTET,
    var venteårsak: Venteårsak? = Venteårsak.UDEFINERT,
    var frist: LocalDateTime? = null,
    val opprettet: LocalDateTime = LocalDateTime.now(),
    val endret: LocalDateTime? = LocalDateTime.now(),
) {
    fun build(): AksjonspunktTilstandDto {
        return AksjonspunktTilstandDto(
            kode,
            status,
            venteårsak,
            "saksbehandler",
            frist,
            opprettet,
            endret
        )
    }

    fun medStatus(status: AksjonspunktStatus): AksjonspunktTilstandBuilder {
        this.status = status
        return this
    }

    fun medVenteårsakOgFrist(venteårsak: Venteårsak?, frist: LocalDateTime? = LocalDateTime.now().plusWeeks(1)): AksjonspunktTilstandBuilder {
        this.venteårsak = venteårsak
        this.frist = frist
        return this
    }

    companion object {
        val KONTROLLER_LEGEERKLÆRING = AksjonspunktDefinisjon.KONTROLLER_LEGEERKLÆRING.builder().medStatus(AksjonspunktStatus.OPPRETTET)
        val VENTER_PÅ_IM = AksjonspunktDefinisjon.AUTO_VENT_ETTERLYST_INNTEKTSMELDING.builder()
            .medStatus(AksjonspunktStatus.OPPRETTET)
            .medVenteårsakOgFrist(Venteårsak.VENTER_PÅ_ETTERLYST_INNTEKTSMELDINGER)
        val FORESLÅ_VEDTAK = AksjonspunktDefinisjon.FORESLÅ_VEDTAK.builder().medStatus(AksjonspunktStatus.OPPRETTET)
        val FATTER_VEDTAK = AksjonspunktDefinisjon.FATTER_VEDTAK.builder().medStatus(AksjonspunktStatus.OPPRETTET)
    }
}

fun AksjonspunktDefinisjon.builder(): AksjonspunktTilstandBuilder {
    return AksjonspunktTilstandBuilder(kode)
}
