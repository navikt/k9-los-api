package no.nav.k9.los.integrasjon.pdl

data class PersonPdlResponse(
        val ikkeTilgang: Boolean,
        val person: PersonPdl?
)
