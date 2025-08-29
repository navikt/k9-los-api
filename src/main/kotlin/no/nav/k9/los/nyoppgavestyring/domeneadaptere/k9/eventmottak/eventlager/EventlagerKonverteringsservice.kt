package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager

import kotliquery.TransactionalSession
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import java.util.UUID

class EventlagerKonverteringsservice(
    private val punsjEventRepositoryPerLinje: EventRepository,
    private val eventRepository: K9PunsjEventRepository,
) {

    fun konverterOppgave(eksternId: String, fagsystem: Fagsystem, tx: TransactionalSession) {
        val gammelModell = eventRepository.hent(UUID.fromString(eksternId))
        var internVersjon = 0
        gammelModell.eventer.forEach { event ->
            punsjEventRepositoryPerLinje.lagre(LosObjectMapper.instance.writeValueAsString(event), fagsystem, internVersjon)
            internVersjon++
        }
    }
}