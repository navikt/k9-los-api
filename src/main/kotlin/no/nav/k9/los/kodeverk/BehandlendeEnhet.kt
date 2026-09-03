package no.nav.k9.los.kodeverk

enum class BehandlendeEnhet(override val kode: String, override val navn: String, override val kodeverk: String): Kodeverdi {
    STYRINGSENHET("4400", "NAV ARBEID OG YTELSER STYRINGSENHET", "BEHANDLENDE_ENHET"),
    KRISTIANIA("4403", "NAV ARBEID OG YTELSER KRISTIANIA", "BEHANDLENDE_ENHET"),
    SØRLANDET("4410", "NAV ARBEID OG YTELSER SØRLANDET", "BEHANDLENDE_ENHET"),
    YTELSESAVDELINGEN("2830", "YTELSESAVDELINGEN", "BEHANDLENDE_ENHET"),
    UKJENT("UKJENT", "Ukjent", "BEHANDLENDE_ENHET");

    companion object {
        fun fraKode(o: Any): BehandlendeEnhet {
            return entries.find { it.kode == o } ?: UKJENT
        }
    }
}

