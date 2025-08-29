package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj

import kotliquery.TransactionalSession
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import java.util.UUID

class EventlagerKonverteringsservice(
    private val punsjEventRepositoryPerLinje: PunsjEventRepositoryPerLinje,
    private val eventRepository: K9PunsjEventRepository,
) {

    fun konverterOppgave(eksternId: String, tx: TransactionalSession) {
        val gammelModell = eventRepository.hent(UUID.fromString(eksternId))
        var internVersjon = 0
        gammelModell.eventer.forEach { event ->
            punsjEventRepositoryPerLinje.lagre(LosObjectMapper.instance.writeValueAsString(event), internVersjon)
            internVersjon++
        }
    }
}