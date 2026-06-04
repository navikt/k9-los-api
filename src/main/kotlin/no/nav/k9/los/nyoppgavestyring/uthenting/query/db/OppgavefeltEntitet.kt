package no.nav.k9.los.nyoppgavestyring.uthenting.query.db

import no.nav.k9.los.nyoppgavestyring.uthenting.query.dto.felter.Oppgavefelt
import no.nav.k9.los.nyoppgavestyring.spi.felter.TransientFeltutleder

data class OppgavefeltMedMer(
    val oppgavefelt: Oppgavefelt,
    val transientFeltutleder: TransientFeltutleder?
)