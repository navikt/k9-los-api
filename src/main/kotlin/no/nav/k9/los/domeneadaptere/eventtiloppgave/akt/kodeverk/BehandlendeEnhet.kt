package no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.kodeverk

enum class BehandlendeEnhet(val kode: String, val navn: String) {
    STYRINGSENHET("4416", "NAV Arbeid og ytelser Trondheim"),
    FALKENBORG("5701", "NAV Arbeids- og tjenestelinjen Falkenborg"),
    LERKENDAL("5702", "NAV Arbeids- og tjenestelinjen Lerkendal"),
    YTELSESAVDELINGEN("2830", "Ytelsesavdelingen"),
    UKJENT("UKJENT", "Ukjent");

    companion object {
        fun fraKode(o: Any): BehandlendeEnhet {
            return entries.find { it.kode == o } ?: UKJENT
        }
    }
}

