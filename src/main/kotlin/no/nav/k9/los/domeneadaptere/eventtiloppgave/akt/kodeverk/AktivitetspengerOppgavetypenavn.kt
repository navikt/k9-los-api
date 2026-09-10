package no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.kodeverk

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavemottak.OppgaveDtoType

enum class AktivitetspengerOppgavetypenavn(@JsonValue override val kode: String) : OppgaveDtoType {
    AKTIVITETSPENGERORDINÆRDEL1("aktivitetspenger-ordinær-del1"),
    AKTIVITETSPENGERORDINÆRDEL2("aktivitetspenger-ordinær-del2");

    override val område: Områder = Områder.AKTIVITETSPENGER

    companion object {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fraKode(kode: String): AktivitetspengerOppgavetypenavn {
            return entries.find { it.kode == kode }
                ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
        }
    }
}