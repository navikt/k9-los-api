package no.nav.k9.los.domeneadaptere.eventlager

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

interface Oppgavetype {
    val kode: String
    val område: Områder
    val fagsystem: Fagsystem
}
