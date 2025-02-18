package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.dagenstall

import no.nav.k9.los.domene.modell.BehandlingType

enum class DagensTallUndergruppe(val navn: String) {
    TOTALT("Totalt"),
    FORSTEGANGSSOKNAD("Førstegangsbehandling"),
    REVURDERING("Revurdering");

    companion object {
        fun fraBehandlingType(behandlingType: BehandlingType): DagensTallUndergruppe {
            return when (behandlingType) {
                BehandlingType.FORSTEGANGSSOKNAD -> FORSTEGANGSSOKNAD
                BehandlingType.REVURDERING -> REVURDERING
                else -> throw IllegalArgumentException("Støtter ikke behandlingstype=$behandlingType")
            }
        }
    }
}