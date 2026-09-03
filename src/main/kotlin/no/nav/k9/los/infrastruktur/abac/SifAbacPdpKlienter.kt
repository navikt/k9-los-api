package no.nav.k9.los.infrastruktur.abac

import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRuter

class SifAbacPdpKlienter(
    k9: SifAbacPdpKlientK9,
    aktivitetspenger: SifAbacPdpKlientAktivitetspenger,
) : OmrådeRuter<ISifAbacPdpKlient>(k9, aktivitetspenger)