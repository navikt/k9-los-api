package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager

import kotliquery.TransactionalSession
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.klage.K9KlageEventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeEventRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import java.util.UUID

class EventlagerKonverteringsservice(
    private val nyttEventrepository: EventRepository,
    private val punsjEventRepository: K9PunsjEventRepository,
    private val klageEventRepository: K9KlageEventRepository,
    private val tilbakeEventRepository: K9TilbakeEventRepository,
    private val sakEventRepository: K9SakEventRepository,
) {

    fun konverterOppgave(eksternId: String, fagsystem: Fagsystem, tx: TransactionalSession) {
        when (fagsystem) {
            Fagsystem.SAK -> {
                val gammelModell = sakEventRepository.hent(UUID.fromString(eksternId))
                gammelModell.eventer.forEach { event ->
                    nyttEventrepository.lagre(LosObjectMapper.instance.writeValueAsString(event), fagsystem, tx)
                }
            }
            Fagsystem.TILBAKE -> {
                val gammelModell = tilbakeEventRepository.hent(UUID.fromString(eksternId))
                gammelModell.eventer.forEach { event ->
                    nyttEventrepository.lagre(LosObjectMapper.instance.writeValueAsString(event), fagsystem, tx)
                }
            }
            Fagsystem.KLAGE -> {
                val gammelModell = klageEventRepository.hent(UUID.fromString(eksternId))
                gammelModell.eventer.forEach { event ->
                    nyttEventrepository.lagre(LosObjectMapper.instance.writeValueAsString(event), fagsystem, tx)
                }
            }
            Fagsystem.PUNSJ -> {
                val gammelModell = punsjEventRepository.hent(UUID.fromString(eksternId))
                gammelModell.eventer.forEach { event ->
                    nyttEventrepository.lagre(LosObjectMapper.instance.writeValueAsString(event), fagsystem, tx)
                }
            }
        }

    }
}