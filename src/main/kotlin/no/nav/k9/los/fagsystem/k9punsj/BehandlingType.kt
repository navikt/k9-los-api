package no.nav.k9.los.fagsystem.k9punsj

enum class BehandlingType(kode: String, navn: String, beskrivelse: String) {
    PAPIRSØKNAD("PAPIRSØKNAD", "Papirsøknad", "PUNSJ_INNSENDING_TYPE"),
    PAPIRETTERSENDELSE("PAPIRETTERSENDELSE", "Papirettersendelse", "PUNSJ_INNSENDING_TYPE"),
    PAPIRINNTEKTSOPPLYSNINGER("PAPIRINNTEKTSOPPLYSNINGER", "Papirinntektsopplysninger", "PUNSJ_INNSENDING_TYPE"),
    DIGITAL_ETTERSENDELSE("DIGITAL_ETTERSENDELSE", "Digital ettersendelse", "PUNSJ_INNSENDING_TYPE"),
    INNLOGGET_CHAT("INNLOGGET_CHAT", "Innlogget chat", "PUNSJ_INNSENDING_TYPE"),
    SKRIV_TIL_OSS_SPØRMSÅL("SKRIV_TIL_OSS_SPØRMSÅL", "Skriv til oss spørsmål", "PUNSJ_INNSENDING_TYPE"),
    SKRIV_TIL_OSS_SVAR("SKRIV_TIL_OSS_SVAR", "Skriv til oss svar", "PUNSJ_INNSENDING_TYPE"),
    SAMTALEREFERAT("SAMTALEREFERAT", "Samtalereferat", "PUNSJ_INNSENDING_TYPE"),
    KOPI("KOPI", "Kopi", "PUNSJ_INNSENDING_TYPE"),
    INNTEKTSMELDING_UTGÅTT("INNTEKTSMELDING_UTGÅTT", "Inntektsmeldinger uten søknad", "PUNSJ_INNSENDING_TYPE"),
    UTEN_FNR_DNR("UTEN_FNR_DNR", "Uten fnr eller dnr", "PUNSJ_INNSENDING_TYPE"),
    UKJENT("UKJENT", "Ukjent", "PUNSJ_INNSENDING_TYPE");
}