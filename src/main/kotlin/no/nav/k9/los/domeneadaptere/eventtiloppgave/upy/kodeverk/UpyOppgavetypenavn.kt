package no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.kodeverk

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import no.nav.k9.los.domeneadaptere.eventlager.Oppgavetype
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavemottak.OppgaveDtoType

enum class UpyOppgavetypenavn(@JsonValue override val kode: String, val navn: String) : OppgaveDtoType, Oppgavetype {
    UNGDOMSPROGRAMYTELSENORDINÆR("ungdomsprogramytelsen-ordinær", "Ordinær"),
    UNGDOMSPROGRAMYTELSENKLAGE("ungdomsprogramytelsen-klage", "Klage");

    override val område: Områder = Områder.UNGDOMSPROGRAMYTELSEN

    companion object {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fraKode(kode: String): UpyOppgavetypenavn {
            return entries.find { it.kode == kode }
                ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
        }
    }
}