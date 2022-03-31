package no.nav.k9.fagsystem.k9punsj.kontrakt

enum class K9PunsjHendelseType(val kode: String) {
    PUNSJ_OPPRETTET(K9PunsjHendelseType.PUNSJ_OPPRETTET_TYPE),
    PUNSJ_AVBRUTT(K9PunsjHendelseType.PUNSJ_AVBRUTT_TYPE),
    PUNSJ_FERDIGSTILT(K9PunsjHendelseType.PUNSJ_FERDIGSTILT_TYPE);

    companion object {
        const val PUNSJ_OPPRETTET_TYPE = "PUNSJ_OPPRETTET"
        const val PUNSJ_AVBRUTT_TYPE = "PUNSJ_AVBRUTT"
        const val PUNSJ_FERDIGSTILT_TYPE = "PUNSJ_FERDIGSTILT"
    }
}