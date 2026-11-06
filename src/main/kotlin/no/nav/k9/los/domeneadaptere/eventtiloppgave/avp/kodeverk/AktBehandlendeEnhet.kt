package no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.kodeverk

enum class AktBehandlendeEnhet(val kode: String, val navn: String) {
    STYRINGSENHET("4416", "Nav Arbeid og Ytelser Trondheim"),
    FALKENBORG("5701", "Nav Falkenborg"),
    LERKENDAL("5702", "Nav Lerkendal"),
    YTELSESAVDELINGEN("2830", "Ytelsesavdelingen"),
    UKJENT("UKJENT", "Ukjent");

    companion object {
        fun fraKode(o: Any): AktBehandlendeEnhet {
            return entries.find { it.kode == o } ?: UKJENT
        }
    }
}

