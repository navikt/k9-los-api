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

    fun tilFeltverdiOperator(): FeltverdiOperator {
        return when (this) {
            EQUALS -> FeltverdiOperator.EQUALS
            LESS_THAN -> FeltverdiOperator.LESS_THAN
            GREATER_THAN -> FeltverdiOperator.GREATER_THAN
            LESS_THAN_OR_EQUALS -> FeltverdiOperator.LESS_THAN_OR_EQUALS
            GREATER_THAN_OR_EQUALS -> FeltverdiOperator.GREATER_THAN_OR_EQUALS
            NOT_EQUALS -> FeltverdiOperator.NOT_EQUALS
            IN -> FeltverdiOperator.IN
            NOT_IN -> FeltverdiOperator.NOT_IN
            INTERVAL -> throw IllegalArgumentException("INTERVAL er ikke støttet som feltverdioperator")
        }
    }
}