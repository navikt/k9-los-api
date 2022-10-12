package no.nav.k9.los.fagsystem.k9punsj.kontrakt

enum class K9PunsjHendelseType(val kode: String) {
    PUNSJ_OPPRETTET("PUNSJ_OPPRETTET"),
    PUNSJ_AVBRUTT("PUNSJ_AVBRUTT"),
    PUNSJ_FERDIGSTILT("PUNSJ_FERDIGSTILT");

    companion object {
        const val PUNSJ_OPPRETTET_TYPE = "PUNSJ_OPPRETTET"
        const val PUNSJ_AVBRUTT_TYPE = "PUNSJ_AVBRUTT"
        const val PUNSJ_FERDIGSTILT_TYPE = "PUNSJ_FERDIGSTILT"
    }
}