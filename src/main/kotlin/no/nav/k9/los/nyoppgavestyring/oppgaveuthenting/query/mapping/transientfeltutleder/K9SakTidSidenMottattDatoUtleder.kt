package no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.query.mapping.transientfeltutleder

import no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.query.db.OmrådeOgKode

class K9SakTidSidenMottattDatoUtleder: LøpendeDurationTransientFeltutleder(
    løpendeTidFelter = listOf(
        OmrådeOgKode("K9", "mottattDato"),
    ))