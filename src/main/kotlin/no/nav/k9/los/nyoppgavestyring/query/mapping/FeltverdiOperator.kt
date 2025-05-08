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

    fun tilEksternFeltverdiOperator(): EksternFeltverdiOperator {
        return when (this) {
            EQUALS -> EksternFeltverdiOperator.EQUALS
            LESS_THAN -> EksternFeltverdiOperator.LESS_THAN
            GREATER_THAN -> EksternFeltverdiOperator.GREATER_THAN
            LESS_THAN_OR_EQUALS -> EksternFeltverdiOperator.LESS_THAN_OR_EQUALS
            GREATER_THAN_OR_EQUALS -> EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS
            NOT_EQUALS -> EksternFeltverdiOperator.NOT_EQUALS
            IN -> EksternFeltverdiOperator.IN
            NOT_IN -> EksternFeltverdiOperator.NOT_IN
        }
    }
}