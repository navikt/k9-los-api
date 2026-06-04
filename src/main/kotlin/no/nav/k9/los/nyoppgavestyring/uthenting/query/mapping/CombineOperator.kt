package no.nav.k9.los.nyoppgavestyring.uthenting.query.mapping

enum class CombineOperator(val sql: String, val kode: String, val defaultValue: Boolean) {
    AND("AND", "AND", true),
    OR("OR", "OR", false);
}