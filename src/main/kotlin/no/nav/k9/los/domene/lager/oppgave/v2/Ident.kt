package no.nav.k9.los.domene.lager.oppgave.v2

data class Ident(
    val id: String,
    val idType: IdType,
) {
    enum class IdType {
        ORGNR,
        AKTØRID,
    }
}