package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9

import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem

enum class K9Oppgavetypenavn(val kode: String) {
    SAK("k9sak"),
    KLAGE("k9klage"),
    TILBAKE("k9tilbake"),
    PUNSJ("k9punsj");

    companion object {
        fun fraFagsystem(fagsystem: Fagsystem): K9Oppgavetypenavn {
            return when (fagsystem) {
                Fagsystem.K9SAK -> SAK
                Fagsystem.K9TILBAKE -> TILBAKE
                Fagsystem.K9KLAGE -> KLAGE
                Fagsystem.PUNSJ -> PUNSJ
            }
        }
    }
}