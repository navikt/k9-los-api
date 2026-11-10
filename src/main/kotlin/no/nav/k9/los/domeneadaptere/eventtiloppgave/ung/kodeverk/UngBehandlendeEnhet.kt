package no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.kodeverk

enum class UngBehandlendeEnhet(val kode: String, val navn: String) {
    NAV_TILTAK_OSLO("0387", "Nav Tiltak Oslo"),
    UKJENT("UKJENT", "Ukjent");

    companion object {
        fun fraKode(o: Any): UngBehandlendeEnhet {
            return entries.find { it.kode == o } ?: UKJENT
        }
    }
}

