package no.nav.k9.los.domeneadaptere.k9.eventmottak.eventlager

import no.nav.k9.los.kodeverk.Fagsystem
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

data class EventNøkkel(
    val fagsystem: Fagsystem,
    val eksternId: String,
    val id: Long? = null,
    val område: Områder
)