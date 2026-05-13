package no.nav.k9.los.nyoppgavestyring.query.mapping.transientfeltutleder

import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.K9FeltIder
import no.nav.k9.los.nyoppgavestyring.query.db.OmrådeOgKode

class K9SakTidSidenMottattDatoUtleder: LøpendeDurationTransientFeltutleder(
    løpendeTidFelter = listOf(
        OmrådeOgKode("K9", K9FeltIder.MOTTATT_DATO),
    ))