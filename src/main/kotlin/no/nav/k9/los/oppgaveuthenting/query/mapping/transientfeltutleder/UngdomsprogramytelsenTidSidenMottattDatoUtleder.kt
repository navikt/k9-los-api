package no.nav.k9.los.oppgaveuthenting.query.mapping.transientfeltutleder

import no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.oppgavedefinisjon.UngdomsprogramytelsenFeltIder
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.db.OmrådeOgKode

class UngdomsprogramytelsenTidSidenMottattDatoUtleder: LøpendeDurationTransientFeltutleder(
    løpendeTidFelter = listOf(
        OmrådeOgKode(Områder.UNGDOMSPROGRAMYTELSEN, UngdomsprogramytelsenFeltIder.Sak.MOTTATT_DATO),
    ))