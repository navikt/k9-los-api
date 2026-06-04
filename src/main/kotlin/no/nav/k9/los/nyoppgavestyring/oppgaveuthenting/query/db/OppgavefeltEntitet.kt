package no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.query.db

import no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.query.dto.felter.Oppgavefelt
import no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.query.mapping.transientfeltutleder.TransientFeltutleder

data class OppgavefeltMedMer(
    val oppgavefelt: Oppgavefelt,
    val transientFeltutleder: TransientFeltutleder?
)