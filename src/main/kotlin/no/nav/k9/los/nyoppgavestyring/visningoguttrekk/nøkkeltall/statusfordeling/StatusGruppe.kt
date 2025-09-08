package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.statusfordeling

enum class StatusGruppe(val tekst: String) {
    BEHANDLINGER("Behandlinger"),
    FØRSTEGANG("Førstegangsbehandlinger"),
    REVURDERING("Revurderinger"),
    FEILUTBETALING("Feilutbetalinger"),
    KLAGE("Klager"),
    PUNSJ("Punsj-oppgaver"),
}