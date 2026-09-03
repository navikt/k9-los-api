package no.nav.k9.los.oppgaveuthenting.query.dto.resultat

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

data class Oppgavefeltverdi(
    val område: Områder?,
    val kode: String,
    val verdi: Any?
)