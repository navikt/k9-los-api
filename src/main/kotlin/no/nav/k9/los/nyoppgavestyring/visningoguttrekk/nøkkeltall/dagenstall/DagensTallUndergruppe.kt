package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.dagenstall

enum class DagensTallUndergruppe(val navn: String) {
    TOTALT("Totalt"),
    FØRSTEGANG("Førstegangsbehandlinger"),
    REVURDERING("Revurderinger"),
    FEILUTBETALING("Feilutbetalinger"),
    KLAGE("Klager"),
    UNNTAKSBEHANDLING("Unntaksbehandlinger"),
    PUNSJ("Punsj-oppgaver");
}