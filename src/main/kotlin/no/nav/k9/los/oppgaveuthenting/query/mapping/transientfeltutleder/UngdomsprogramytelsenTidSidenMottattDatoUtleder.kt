package no.nav.k9.los.oppgaveuthenting.query.mapping.transientfeltutleder

import no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.oppgavedefinisjon.UngdomsprogramytelsenFelter
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.db.OmrådeOgKode

class UngdomsprogramytelsenTidSidenMottattDatoUtleder: LøpendeDurationTransientFeltutleder(
    løpendeTidFelter = listOf(
        OmrådeOgKode(Områder.UNGDOMSPROGRAMYTELSEN, UngdomsprogramytelsenFelter.Sak.MOTTATT_DATO.id),
    ))