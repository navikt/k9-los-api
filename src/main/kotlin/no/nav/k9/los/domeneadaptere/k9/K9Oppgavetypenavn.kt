package no.nav.k9.los.domeneadaptere.k9

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import no.nav.k9.los.domeneadaptere.eventlager.Fagsystem
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavemottak.OppgaveDtoType

enum class K9Oppgavetypenavn(@JsonValue override val kode: String) : OppgaveDtoType {
    SAK("k9sak"),
    KLAGE("k9klage"),
    TILBAKE("k9tilbake"),
    PUNSJ("k9punsj");

    override val område: Områder = Områder.K9

    companion object {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fraKode(kode: String): K9Oppgavetypenavn {
            return entries.find { it.kode == kode } ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
        }

        fun fraFagsystem(fagsystem: Fagsystem): K9Oppgavetypenavn {
            return when (fagsystem) {
                Fagsystem.K9SAK -> SAK
                Fagsystem.K9TILBAKE -> TILBAKE
                Fagsystem.K9KLAGE -> KLAGE
                Fagsystem.PUNSJ -> PUNSJ
                Fagsystem.UNGSAK, Fagsystem.UNGTILBAKE -> throw IllegalStateException("Fagsystem ${Fagsystem} er ikke aktuelt for k9-codepath")
            }
        }
    }
}