package no.nav.k9.los.oppgaveuthenting.query.db

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

data class OmrådeOgKode (
    val område: Områder?,
    val kode: String
)