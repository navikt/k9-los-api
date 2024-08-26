package no.nav.k9.los.nyoppgavestyring.query.mapping

enum class EksternFeltverdiOperator(val kode: String) {
    EQUALS("EQUALS"),
    LESS_THAN("LESS_THAN"),
    GREATER_THAN("GREATER_THAN"),
    LESS_THAN_OR_EQUALS("LESS_THAN_OR_EQUALS"),
    GREATER_THAN_OR_EQUALS("GREATER_THAN_OR_EQUALS"),
    NOT_EQUALS("NOT_EQUALS"),
    IN("IN"),
    NOT_IN("NOT_IN"),
    INTERVAL("RANGE");
}