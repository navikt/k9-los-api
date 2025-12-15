package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventLagret
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventNøkkel
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem

fun EventRepository.lagre(fagsystem: Fagsystem, event: K9PunsjEventDto, tx: TransactionalSession): EventLagret? {
    return this.lagre(fagsystem, event.eksternId.toString(), event.eventTid.toString(), LosObjectMapper.instance.writeValueAsString(event), tx)
}

fun EventRepository.endreEvent(nøkkelId: Long, eventNøkkel: EventNøkkel, event: String, tx: TransactionalSession): EventLagret? {
    val tree = LosObjectMapper.instance.readTree(event)
    val eksternVersjon = tree.findValue("eventTid").asText()

    tx.run(
        queryOf(
            """
                        update event set "data" = :data :: jsonb
                        where event_nokkel_id = :event_nokkel_id 
                     """,
            mapOf(
                "event_nokkel_id" to nøkkelId,
                "data" to event
            )
        ).asUpdate
    )

    return hent(eventNøkkel.fagsystem, eventNøkkel.eksternId, eksternVersjon, tx)
}