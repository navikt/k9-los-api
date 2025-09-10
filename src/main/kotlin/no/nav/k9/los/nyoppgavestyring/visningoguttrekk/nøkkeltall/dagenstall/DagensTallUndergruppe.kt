package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.dagenstall

import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType

enum class DagensTallUndergruppe(val navn: String) {
    TOTALT("Totalt"),
    FØRSTEGANG("Førstegangsbehandlinger"),
    REVURDERING("Revurderinger"),
    FEILUTBETALING("Feilutbetalinger"),
    KLAGE("Klager"),
    UNNTAKSBEHANDLING("Unntaksbehandlinger"),
    PUNSJ("Punsj-oppgaver"),
    ;

    companion object {
        fun fraBehandlingType(behandlingType: BehandlingType): DagensTallUndergruppe {
            return when (behandlingType) {
                BehandlingType.FORSTEGANGSSOKNAD -> FØRSTEGANG
                BehandlingType.KLAGE -> KLAGE
                BehandlingType.REVURDERING -> REVURDERING
                BehandlingType.TILBAKE -> FEILUTBETALING
                BehandlingType.REVURDERING_TILBAKEKREVING -> FEILUTBETALING
                BehandlingType.UNNTAKSBEHANDLING -> UNNTAKSBEHANDLING
                else -> throw IllegalArgumentException("Støtter ikke behandlingstype=$behandlingType")
            }
        }

        fun forPunsj(): DagensTallUndergruppe {
            return PUNSJ
        }
    }
}