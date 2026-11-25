package no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.kodeverk

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import no.nav.k9.los.domeneadaptere.eventlager.Fagsystem
import no.nav.k9.los.domeneadaptere.eventlager.Oppgavetype
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavemottak.OppgaveDtoType

enum class UngOppgavetypenavn(@JsonValue override val kode: String, val navn: String) : OppgaveDtoType, Oppgavetype {
    UNGDOMSPROGRAMYTELSENORDINÆR("ungdomsprogramytelsen-ordinær", "Ordinær"),
    UNGDOMSPROGRAMYTELSENKLAGE("ungdomsprogramytelsen-klage", "Klage"),
    UNGDOMSPROGRAMYTELSENTILBAKE("ungdomsprogramytelsen-tilbake", "Tilbakekreving");

    override val område: Områder = Områder.UNGDOMSPROGRAMYTELSEN
    override val fagsystem: Fagsystem
        get() = if (this == UNGDOMSPROGRAMYTELSENTILBAKE) Fagsystem.UNGTILBAKE else Fagsystem.UNGSAK

    companion object {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fraKode(kode: String): UngOppgavetypenavn {
            return entries.find { it.kode == kode }
                ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
        }
    }
}