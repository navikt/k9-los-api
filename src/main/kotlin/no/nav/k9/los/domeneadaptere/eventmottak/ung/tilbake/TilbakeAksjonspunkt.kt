package no.nav.k9.los.domeneadaptere.eventmottak.ung.tilbake

import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.KodeverkDto
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.KodeverkVerdiDto
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.Synlighet.OVER_STREKEN
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

fun tilbakeAksjonspunktKodeverk(område: Områder): KodeverkDto = KodeverkDto(
    område = område,
    eksternId = TilbakeAksjonspunkt::class.java.simpleName,
    beskrivelse = "Aksjonspunkter for tilbakekreving",
    uttømmende = false,
    verdier = TilbakeAksjonspunkt.entries.map {
        KodeverkVerdiDto(verdi = it.kode, visningsnavn = it.navn, synlighet = OVER_STREKEN)
    },
)

fun tilbakeAksjonspunktverdierForFellesKodeverk(): List<KodeverkVerdiDto> =
    TilbakeAksjonspunkt.entries.map {
        KodeverkVerdiDto(
            verdi = it.feltkode,
            visningsnavn = "Tilbake: ${it.navn}",
            synlighet = OVER_STREKEN,
        )
    }

enum class TilbakeAksjonspunkt(val kode: String, val navn: String, val erAutopunkt: Boolean = false) {
    VURDER_TILBAKEKREVING("5002", "Vurder tilbakekreving"),
    VURDER_FORELDELSE("5003", "Vurder foreldelse"),
    FORESLA_VEDTAK("5004", "Foreslå vedtak"),
    FATTE_VEDTAK("5005", "Fatte vedtak"),
    AVKLAR_VERGE("5030", "Avklar verge"),
    AVKLAR_FAKTA("7003", "Avklar fakta for feilutbetaling"),
    VENT_PA_BRUKER("7001", "Venter på tilbakemelding fra bruker", true),
    VENT_PA_GRUNNLAG("7002", "Venter på tilbakekrevingsgrunnlag fra økonomi", true);

    val feltkode: String get() = "TILBAKE_$kode"

    companion object {
        fun fraKode(kode: String): TilbakeAksjonspunkt =
            entries.firstOrNull { it.kode == kode } ?: throw IllegalArgumentException("Ukjent tilbake-aksjonspunkt: $kode")
    }
}
