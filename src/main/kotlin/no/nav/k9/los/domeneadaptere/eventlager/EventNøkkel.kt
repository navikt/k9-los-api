package no.nav.k9.los.domeneadaptere.eventlager

import no.nav.k9.los.domeneadaptere.eventlager.Fagsystem
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

data class EventNøkkel(
    val fagsystem: Fagsystem,
    val eksternId: String,
    val id: Long? = null
)