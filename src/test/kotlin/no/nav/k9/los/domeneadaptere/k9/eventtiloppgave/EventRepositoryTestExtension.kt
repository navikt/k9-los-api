package no.nav.k9.los.domeneadaptere.k9.eventtiloppgave

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.domeneadaptere.k9.eventmottak.eventlager.EventLagret
import no.nav.k9.los.domeneadaptere.k9.eventmottak.eventlager.EventNøkkel
import no.nav.k9.los.domeneadaptere.k9.eventmottak.eventlager.EventRepository
import no.nav.k9.los.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventDto
import no.nav.k9.los.domeneadaptere.k9.eventmottak.sak.K9SakEventDto
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.kodeverk.Fagsystem
import java.time.LocalDateTime

fun EventRepository.lagre(fagsystem: Fagsystem, event: K9PunsjEventDto, tx: TransactionalSession): EventNøkkel {
    return this.lagre(fagsystem, event.eksternId.toString(), event.eventTid.toString(), LosObjectMapper.instance.writeValueAsString(event), tx)
}

fun EventRepository.lagre(fagsystem: Fagsystem, event: K9SakEventDto, tx: TransactionalSession): EventNøkkel {
    return this.lagre(fagsystem, event.eksternId.toString(), event.eventTid.toString(), LosObjectMapper.instance.writeValueAsString(event), tx)
}

fun EventRepository.endreEvent(eventnøkkel: EventNøkkel, event: String, tx: TransactionalSession): EventLagret? {
    val tree = LosObjectMapper.instance.readTree(event)
    // ekstern_versjon lagres som LocalDateTime.toString() (se lagre-funksjonene over og eventhandlerne i main).
    // Jackson serialiserer LocalDateTime uten etterfølgende nuller i nanodelen (f.eks. "12:00:00.1"),
    // mens LocalDateTime.toString() padder til grupper på 3 siffer ("12:00:00.100"). Vi må derfor parse
    // og normalisere, ellers finner ikke oppslaget under raden (flakete NPE).
    val eksternVersjon = LocalDateTime.parse(tree.findValue("eventTid").asText()).toString()

    tx.run(
        queryOf(
            """
                        update event set "data" = :data :: jsonb
                        where event_nokkel_id = :event_nokkel_id 
                     """,
            mapOf(
                "event_nokkel_id" to eventnøkkel.id,
                "data" to event
            )
        ).asUpdate
    )

    return hent(eventnøkkel.fagsystem, eventnøkkel.eksternId, eksternVersjon, tx)
}