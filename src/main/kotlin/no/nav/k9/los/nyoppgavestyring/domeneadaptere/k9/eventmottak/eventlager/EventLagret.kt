package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.klage.K9KlageEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeEventDto
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import java.time.LocalDateTime

sealed class EventLagret(
    open val nøkkelId: Long,
    open val fagsystem: Fagsystem,
    open val eksternId: String,
    open val eksternVersjon: String,
    open val eventJson: String,
    open val opprettet: LocalDateTime,
    open val dirty: Boolean,
) {
    companion object {
        fun create(
            nøkkelId: Long,
            fagsystem: Fagsystem,
            eksternId: String,
            eksternVersjon: String,
            eventJson: String,
            opprettet: LocalDateTime,
            dirty: Boolean
        ): EventLagret = when (fagsystem) {
            Fagsystem.K9SAK -> K9Sak(nøkkelId, eksternId, eksternVersjon, eventJson, opprettet, dirty)
            Fagsystem.K9KLAGE -> K9Klage(nøkkelId, eksternId, eksternVersjon, eventJson, opprettet, dirty)
            Fagsystem.PUNSJ -> K9Punsj(nøkkelId, eksternId, eksternVersjon, eventJson, opprettet, dirty)
            Fagsystem.K9TILBAKE -> K9Tilbake(nøkkelId, eksternId, eksternVersjon, eventJson, opprettet, dirty)
        }
    }

    data class K9Sak(
        override val nøkkelId: Long,
        override val eksternId: String,
        override val eksternVersjon: String,
        override val eventJson: String,
        override val opprettet: LocalDateTime,
        override val dirty: Boolean,
    ) : EventLagret(nøkkelId, Fagsystem.K9SAK, eksternId, eksternVersjon, eventJson, opprettet, dirty) {
        val eventDto: K9SakEventDto by lazy { LosObjectMapper.instance.readValue(eventJson) }
    }

    data class K9Klage(
        override val nøkkelId: Long,
        override val eksternId: String,
        override val eksternVersjon: String,
        override val eventJson: String,
        override val opprettet: LocalDateTime,
        override val dirty: Boolean,
    ) : EventLagret(nøkkelId, Fagsystem.K9KLAGE, eksternId, eksternVersjon, eventJson, opprettet, dirty) {
        val eventDto: K9KlageEventDto by lazy { LosObjectMapper.instance.readValue(eventJson) }
    }

    data class K9Punsj(
        override val nøkkelId: Long,
        override val eksternId: String,
        override val eksternVersjon: String,
        override val eventJson: String,
        override val opprettet: LocalDateTime,
        override val dirty: Boolean,
    ) : EventLagret(nøkkelId, Fagsystem.PUNSJ, eksternId, eksternVersjon, eventJson, opprettet, dirty) {
        val eventDto: K9PunsjEventDto by lazy { LosObjectMapper.instance.readValue(eventJson) }
    }

    data class K9Tilbake(
        override val nøkkelId: Long,
        override val eksternId: String,
        override val eksternVersjon: String,
        override val eventJson: String,
        override val opprettet: LocalDateTime,
        override val dirty: Boolean,
    ) : EventLagret(nøkkelId, Fagsystem.K9TILBAKE, eksternId, eksternVersjon, eventJson, opprettet, dirty) {
        val eventDto: K9TilbakeEventDto by lazy { LosObjectMapper.instance.readValue(eventJson) }
    }
}