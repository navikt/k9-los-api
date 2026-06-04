package no.nav.k9.los.nyoppgavestyring.uthenting.query.mapping.transientfeltutleder

import no.nav.k9.los.nyoppgavestyring.uthenting.query.db.OmrådeOgKode

class K9SakTidSidenMottattDatoUtleder: LøpendeDurationTransientFeltutleder(
    løpendeTidFelter = listOf(
        OmrådeOgKode("K9", "mottattDato"),
    ))