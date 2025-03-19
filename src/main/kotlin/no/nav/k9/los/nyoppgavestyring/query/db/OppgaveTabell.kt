package no.nav.k9.los.nyoppgavestyring.query.db

enum class OppgaveTabell(val oppgavetabell: String, val verditabell: String) {
    OPPGAVE_PARTISJONERT("oppgave_v3", "oppgavefelt_verdi_part"),
    OPPGAVE_AKTIV("oppgave_v3_aktiv", "oppgavefelt_verdi_aktiv"),
}
