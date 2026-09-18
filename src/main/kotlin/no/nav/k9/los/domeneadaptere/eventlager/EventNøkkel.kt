package no.nav.k9.los.domeneadaptere.eventlager

data class EventNøkkel(
    val fagsystem: Fagsystem,
    val eksternId: String,
    val id: Long? = null
)