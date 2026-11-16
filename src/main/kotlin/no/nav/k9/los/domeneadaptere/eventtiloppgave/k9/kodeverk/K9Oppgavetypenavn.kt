package no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.kodeverk

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import no.nav.k9.los.domeneadaptere.eventlager.Fagsystem
import no.nav.k9.los.domeneadaptere.eventlager.Oppgavetype
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavemottak.OppgaveDtoType

enum class K9Oppgavetypenavn(@JsonValue override val kode: String, override val fagsystem: Fagsystem) : OppgaveDtoType, Oppgavetype {
    SAK("k9sak", Fagsystem.K9SAK),
    KLAGE("k9klage", Fagsystem.K9KLAGE),
    TILBAKE("k9tilbake", Fagsystem.K9TILBAKE),
    PUNSJ("k9punsj", Fagsystem.PUNSJ);

    override val område: Områder = Områder.K9

    companion object {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fraKode(kode: String): K9Oppgavetypenavn {
            return entries.find { it.kode == kode } ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
        }

        fun fraFagsystem(fagsystem: Fagsystem): K9Oppgavetypenavn {
            return entries.find { it.fagsystem == fagsystem }
                ?: throw IllegalStateException("Fagsystem $fagsystem er ikke aktuelt for k9-codepath")
        }
    }
}