package no.nav.k9.los.domeneadaptere.eventlager

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.k9.klage.kodeverk.behandling.oppgavetillos.EventHendelse as KlageEventHendelse
import no.nav.k9.los.domeneadaptere.eventmottak.EventHendelse as LosEventHendelse
import no.nav.ung.kodeverk.hendelse.EventHendelse as UngEventHendelse
import no.nav.k9.los.domeneadaptere.eventmottak.k9.klage.K9KlageEventDto
import no.nav.k9.los.domeneadaptere.eventmottak.k9.punsj.K9PunsjEventDto
import no.nav.k9.los.domeneadaptere.eventmottak.k9.sak.K9SakEventDto
import no.nav.k9.los.domeneadaptere.eventmottak.k9.tilbakekrav.K9TilbakeEventDto
import no.nav.k9.los.domeneadaptere.eventmottak.ung.sak.UngSakEventDto
import no.nav.k9.los.infrastruktur.utils.IkkeImplementertException
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.ung.kodeverk.behandling.FagsakYtelseType
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
    abstract val område: Områder

    /** Vaskeeventer skal ikke telle som ny oppgaveversjon, men korrigere en eksisterende. */
    abstract val erVaskeevent: Boolean

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
            Fagsystem.UNGSAK -> UngSak(nøkkelId, eksternId, eksternVersjon, eventJson, opprettet, dirty)
            Fagsystem.UNGTILBAKE -> throw IkkeImplementertException("Fagsystem $fagsystem is not implemented yet")
        }
    }

    data class K9Sak(
        override val nøkkelId: Long,
        override val eksternId: String,
        override val eksternVersjon: String,
        override val eventJson: String,
        override val opprettet: LocalDateTime,
        override val dirty: Boolean,
        override val område: Områder = Områder.K9,
        private val beriketEventDto: K9SakEventDto? = null,
    ) : EventLagret(nøkkelId, Fagsystem.K9SAK, eksternId, eksternVersjon, eventJson, opprettet, dirty) {
        val eventDto: K9SakEventDto by lazy { beriketEventDto ?: LosObjectMapper.instance.readValue(eventJson) }

        /** Erstatter eventet med en beriket variant. Se EventBeriker – eventJson beholdes urørt. */
        fun beriketMed(eventDto: K9SakEventDto): K9Sak = copy(beriketEventDto = eventDto)

        override val erVaskeevent: Boolean
            get() = eventDto.eventHendelse == LosEventHendelse.VASKEEVENT
    }

    data class K9Klage(
        override val nøkkelId: Long,
        override val eksternId: String,
        override val eksternVersjon: String,
        override val eventJson: String,
        override val opprettet: LocalDateTime,
        override val dirty: Boolean,
        override val område: Områder = Områder.K9,
        private val beriketEventDto: K9KlageEventDto? = null,
    ) : EventLagret(nøkkelId, Fagsystem.K9KLAGE, eksternId, eksternVersjon, eventJson, opprettet, dirty) {
        val eventDto: K9KlageEventDto by lazy { beriketEventDto ?: LosObjectMapper.instance.readValue(eventJson) }

        /** Erstatter eventet med en beriket variant. Se EventBeriker – eventJson beholdes urørt. */
        fun beriketMed(eventDto: K9KlageEventDto): K9Klage = copy(beriketEventDto = eventDto)

        override val erVaskeevent: Boolean
            get() = eventDto.eventHendelse == KlageEventHendelse.VASKEEVENT
    }

    data class K9Punsj(
        override val nøkkelId: Long,
        override val eksternId: String,
        override val eksternVersjon: String,
        override val eventJson: String,
        override val opprettet: LocalDateTime,
        override val dirty: Boolean,
        override val område: Områder = Områder.K9
    ) : EventLagret(nøkkelId, Fagsystem.PUNSJ, eksternId, eksternVersjon, eventJson, opprettet, dirty) {
        val eventDto: K9PunsjEventDto by lazy { LosObjectMapper.instance.readValue(eventJson) }

        // Punsj-eventer har ikke eventHendelse, og sender aldri vaskeeventer.
        override val erVaskeevent: Boolean = false
    }

    data class K9Tilbake(
        override val nøkkelId: Long,
        override val eksternId: String,
        override val eksternVersjon: String,
        override val eventJson: String,
        override val opprettet: LocalDateTime,
        override val dirty: Boolean,
        override val område: Områder = Områder.K9
    ) : EventLagret(nøkkelId, Fagsystem.K9TILBAKE, eksternId, eksternVersjon, eventJson, opprettet, dirty) {
        val eventDto: K9TilbakeEventDto by lazy { LosObjectMapper.instance.readValue(eventJson) }

        override val erVaskeevent: Boolean
            get() = eventDto.eventHendelse == LosEventHendelse.VASKEEVENT
    }

    data class UngSak(
        override val nøkkelId: Long,
        override val eksternId: String,
        override val eksternVersjon: String,
        override val eventJson: String,
        override val opprettet: LocalDateTime,
        override val dirty: Boolean
    ) : EventLagret(nøkkelId, Fagsystem.UNGSAK, eksternId, eksternVersjon, eventJson, opprettet, dirty) {
        val eventDto: UngSakEventDto by lazy { LosObjectMapper.instance.readValue(eventJson) }

        override val erVaskeevent: Boolean
            get() = eventDto.eventHendelse == UngEventHendelse.VASKEEVENT

        override val område: Områder by lazy {
            when (FagsakYtelseType.fraKode(eventDto.ytelseTypeKode)) {
                FagsakYtelseType.AKTIVITETSPENGER -> Områder.AKTIVITETSPENGER
                else -> throw IllegalArgumentException("Ukjent område for UNGSAK event med ytelseType=${eventDto.ytelseTypeKode}")
            }
        }
    }
}