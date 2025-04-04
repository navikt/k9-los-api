package no.nav.k9.los.tjenester.saksbehandler.oppgave

import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.PersonPdl
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.fnr
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.navn
import java.time.LocalDateTime
import java.util.*

data class BehandletOppgave(
    val behandlingId: Long?,
    val saksnummer: String,
    val journalpostId: String?,
    val eksternId: UUID,
    val personnummer: String?,
    val navn: String?,
    val system: String?,
    var timestamp: LocalDateTime = LocalDateTime.now()
) {
        constructor(oppgave: Oppgave, person: PersonPdl): this (
            behandlingId = oppgave.behandlingId,
            saksnummer = oppgave.fagsakSaksnummer,
            journalpostId = oppgave.journalpostId,
            eksternId = oppgave.eksternId,
            personnummer = person.fnr(),
            navn = person.navn(),
            system = oppgave.system,
        )
}

