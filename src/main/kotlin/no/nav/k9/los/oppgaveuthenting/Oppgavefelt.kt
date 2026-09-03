package no.nav.k9.los.oppgaveuthenting

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

data class Oppgavefelt(
    val eksternId: String,
    val område: Områder,
    val listetype: Boolean,
    val påkrevd: Boolean,
    val verdi: String,
    val verdiBigInt: Long?
)