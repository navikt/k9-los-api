package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.dagenstall

import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType

enum class DagensTallUndergruppe(val navn: String) {
    TOTALT("Totalt"),
    FORSTEGANGSSOKNAD("Førstegangsbehandling"),
    REVURDERING("Revurdering"),
    TILBAKE("Tilbakekreving"),
    KLAGE("Klage"),
    INNSYN("Innsyn"),
    ANKE("Anke"),
    REVURDERING_TILBAKEKREVING("Revurdering tilbakekreving"),
    UNNTAKSBEHANDLING("Unntaksbehandling"),
    ;

    companion object {
        fun fraBehandlingType(behandlingType: BehandlingType): DagensTallUndergruppe {
            return when (behandlingType) {
                BehandlingType.FORSTEGANGSSOKNAD -> FORSTEGANGSSOKNAD
                BehandlingType.KLAGE -> KLAGE
                BehandlingType.REVURDERING -> REVURDERING
                BehandlingType.INNSYN -> INNSYN
                BehandlingType.TILBAKE -> TILBAKE
                BehandlingType.ANKE -> ANKE
                BehandlingType.REVURDERING_TILBAKEKREVING -> REVURDERING_TILBAKEKREVING
                BehandlingType.UNNTAKSBEHANDLING -> UNNTAKSBEHANDLING
                else -> throw IllegalArgumentException("Støtter ikke behandlingstype=$behandlingType")
            }
        }
    }
}