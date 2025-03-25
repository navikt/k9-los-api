package no.nav.k9.los.nyoppgavestyring.query.db

enum class Spørringstrategi(val navn: String, val beskrivelse: String, val verditabell: String, val joinuttrykk: String) {
    PARTISJONERT(
        "Partisjonert",
        "Partisjonerte tabeller for alle oppgaver. Tabeller: oppgave_v3_part og oppgavefelt_verdi_part.",
        "oppgavefelt_verdi_part",
        "ov.oppgave_ekstern_id = o.oppgave_ekstern_id AND ov.oppgave_ekstern_versjon = o.oppgave_ekstern_versjon"
    ),
    AKTIV(
        "Aktiv",
        "Aktivtabeller for åpne/ventende oppgaver. Tabeller: oppgave_v3_aktiv og oppgavefelt_verdi_aktiv.",
        "oppgavefelt_verdi_aktiv",
        "ov.oppgave_id = o.id"
    );

    companion object {
        /** Metode som returnerer nullable Spørringstrategi, i motsetning til valueOf som kaster exception */
        fun fraKode(kode: String) = entries.firstOrNull { it.name == kode }
    }
}
