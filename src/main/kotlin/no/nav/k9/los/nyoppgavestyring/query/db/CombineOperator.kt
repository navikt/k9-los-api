package no.nav.k9.los.nyoppgavestyring.query.db

enum class CombineOperator(val sql: String, val defaultValue: Boolean) {
    AND("AND", true),
    OR("OR", false);
}