package no.nav.k9.los.oppgavemottak

import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.kodeverk.K9Oppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.kodeverk.AvpOppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.kodeverk.UngOppgavetypenavn
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

interface OppgaveDtoType {
    val kode: String
    val område: Områder

    companion object {
        fun fra(område: Områder, eksternId: String): OppgaveDtoType = when (område) {
            Områder.K9 -> K9Oppgavetypenavn.fraKode(eksternId)
            Områder.AKTIVITETSPENGER -> AvpOppgavetypenavn.fraKode(eksternId)
            Områder.UNGDOMSPROGRAMYTELSEN -> UngOppgavetypenavn.fraKode(eksternId)
        }

        fun fraEksternId(områdeEksternId: String, eksternId: String): OppgaveDtoType =
            fra(Områder.fraEksternId(områdeEksternId), eksternId)
    }
}

data class GeneriskOppgaveDtoType(
    override val kode: String,
    override val område: Områder,
) : OppgaveDtoType


