package no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.kodeverk

enum class UpyBehandlendeEnhet(val kode: String, val navn: String) {
    NAV_TILTAK_OSLO("0387", "NAV Tiltak Oslo"),
    UKJENT("UKJENT", "Ukjent");

    companion object {
        fun fraKode(o: Any): UpyBehandlendeEnhet {
            return entries.find { it.kode == o } ?: UKJENT
        }
    }
}

