package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.klage.K9KlageEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeEventDto
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import java.time.LocalDateTime

data class EventLagret(
    val id: Long,
    val fagsystem: Fagsystem,
    val eksternId: String,
    val eksternVersjon: String,
    val eventNrForOppgave: Int,
    val eventJson: String,
    val opprettet: LocalDateTime,
) {
    fun hentEvent() {
        when (fagsystem) {
            Fagsystem.SAK -> LosObjectMapper.instance.readValue<K9SakEventDto>(this.eventJson)
            Fagsystem.TILBAKE -> LosObjectMapper.instance.readValue<K9TilbakeEventDto>(this.eventJson)
            Fagsystem.KLAGE -> LosObjectMapper.instance.readValue<K9KlageEventDto>(this.eventJson)
            Fagsystem.PUNSJ -> LosObjectMapper.instance.readValue<PunsjEventDto>(this.eventJson)
        }
    }

    fun hentPunsjEvent(): PunsjEventDto {
        if (fagsystem != Fagsystem.PUNSJ) {
            throw IllegalStateException()
        }
        return LosObjectMapper.instance.readValue<PunsjEventDto>(eventJson)
    }
}
