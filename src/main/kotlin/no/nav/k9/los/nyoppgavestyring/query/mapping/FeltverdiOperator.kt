package no.nav.k9.los.nyoppgavestyring.query.mapping

enum class FeltverdiOperator(val sql: String, val negasjonAv : FeltverdiOperator? = null) {
    EQUALS("="),
    LESS_THAN("<"),
    GREATER_THAN(">"),
    LESS_THAN_OR_EQUALS("<="),
    GREATER_THAN_OR_EQUALS(">="),
    NOT_EQUALS("<>", negasjonAv = EQUALS),
    IN("IN"),
    NOT_IN("NOT IN", negasjonAv = IN);
}