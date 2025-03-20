package no.nav.k9.los.nyoppgavestyring.query.db

enum class OppgavefeltVerdiTabell(val verditabell: String, val joinuttrykk: String) {
    OPPGAVE_PARTISJONERT("oppgavefelt_verdi_part", "ov.oppgave_ekstern_id = o.oppgave_ekstern_id AND ov.oppgave_ekstern_versjon = o.oppgave_ekstern_versjon"),
    OPPGAVE_AKTIV("oppgavefelt_verdi_aktiv", "ov.oppgave_id = o.id"),
}
