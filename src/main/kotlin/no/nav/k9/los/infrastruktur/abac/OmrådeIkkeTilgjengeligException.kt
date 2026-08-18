package no.nav.k9.los.infrastruktur.abac

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

class OmrådeIkkeTilgjengeligException(område: Områder) :
    RuntimeException("Tilgangskontroll for område ${område.eksternId} er ikke tilgjengelig")
