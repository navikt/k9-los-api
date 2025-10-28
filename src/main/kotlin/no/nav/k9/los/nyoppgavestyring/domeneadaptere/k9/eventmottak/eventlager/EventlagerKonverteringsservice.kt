package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager

import kotliquery.TransactionalSession
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.klage.K9KlageEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.klage.K9KlageEventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeEventRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

class EventlagerKonverteringsservice(
    private val nyttEventrepository: EventRepository,
    private val punsjEventRepository: K9PunsjEventRepository,
    private val klageEventRepository: K9KlageEventRepository,
    private val tilbakeEventRepository: K9TilbakeEventRepository,
    private val sakEventRepository: K9SakEventRepository,
) {
    private val log: Logger = LoggerFactory.getLogger(EventlagerKonverteringsservice::class.java)

    fun konverterOppgave(eksternId: String, fagsystem: Fagsystem, tx: TransactionalSession, batchkontekst: Boolean = false) {
        if (!batchkontekst) {
            log.info("Konverterer oppgave med eksternId: $eksternId, fagsystem: $fagsystem")
        }
        when (fagsystem) {
            Fagsystem.K9SAK -> {
                val gammelModell = sakEventRepository.hentMedLås(tx, UUID.fromString(eksternId))
                gammelModell.eventer.forEach { event ->
                    nyttEventrepository.lagre(Fagsystem.K9SAK,LosObjectMapper.instance.writeValueAsString(event), tx)
                }
            }
            Fagsystem.K9TILBAKE -> {
                val gammelModell = tilbakeEventRepository.hentMedLås(tx, UUID.fromString(eksternId))
                gammelModell.eventer.forEach { event ->
                    nyttEventrepository.lagre(Fagsystem.K9TILBAKE, LosObjectMapper.instance.writeValueAsString(event), tx)
                }
            }
            Fagsystem.K9KLAGE -> {
                val gammelModell = klageEventRepository.hentMedLås(tx, UUID.fromString(eksternId))
                gammelModell.eventer.forEach { event ->
                    val eventLagret = nyttEventrepository.lagre(Fagsystem.K9KLAGE, LosObjectMapper.instance.writeValueAsString(event), tx)
                }
            }
            Fagsystem.PUNSJ -> {
                val gammelModell = punsjEventRepository.hentMedLås(tx, UUID.fromString(eksternId))
                gammelModell.eventer.forEach { event ->
                    nyttEventrepository.lagre(Fagsystem.PUNSJ, LosObjectMapper.instance.writeValueAsString(event), tx)
                }
            }
        }

    }

    fun konverterEvent(event: K9SakEventDto, tx: TransactionalSession, batchkontekst: Boolean = false) {
        if (!batchkontekst) {
            log.info("Konverterer oppgave med eksternId: ${event.eksternId}, fagsystem: K9SAK")
        }
        nyttEventrepository.lagre(Fagsystem.K9SAK, LosObjectMapper.instance.writeValueAsString(event), tx)
    }

    fun konverterEvent(event: PunsjEventDto, tx: TransactionalSession, batchkontekst: Boolean = false) {
        if (!batchkontekst) {
            log.info("Konverterer oppgave med eksternId: ${event.eksternId}, fagsystem: PUNSJ")
        }
        nyttEventrepository.lagre(Fagsystem.PUNSJ, LosObjectMapper.instance.writeValueAsString(event), tx)
    }

    fun konverterEvent(event: K9TilbakeEventDto, tx: TransactionalSession, batchkontekst: Boolean = false) {
        if (!batchkontekst) {
            log.info("Konverterer oppgave med eksternId: ${event.eksternId}, fagsystem: K9TILBAKE")
        }
        nyttEventrepository.lagre(Fagsystem.K9TILBAKE, LosObjectMapper.instance.writeValueAsString(event), tx)
    }

    fun konverterEvent(event: K9KlageEventDto, tx: TransactionalSession, batchkontekst: Boolean = false) {
        if (!batchkontekst) {
            log.info("Konverterer oppgave med eksternId: ${event.eksternId}, fagsystem: K9KLAGE")
        }
        nyttEventrepository.lagre(Fagsystem.K9KLAGE, LosObjectMapper.instance.writeValueAsString(event), tx)

    }

}