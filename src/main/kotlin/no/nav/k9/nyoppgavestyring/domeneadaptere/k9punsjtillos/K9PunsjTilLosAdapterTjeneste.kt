package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9punsjtillos

import no.nav.k9.kodeverk.behandling.BehandlingStatus
import no.nav.k9.los.Configuration
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.PunsjEventK9Repository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeTjeneste
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class K9PunsjTilLosAdapterTjeneste(
    private val punsjEventK9Repository: PunsjEventK9Repository,
    private val områdeRepository: OmrådeRepository,
    private val feltdefinisjonTjeneste: FeltdefinisjonTjeneste,
    private val oppgavetypeTjeneste: OppgavetypeTjeneste,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val config: Configuration,
    private val transactionalManager: TransactionalManager
) {
    private val log: Logger = LoggerFactory.getLogger(K9SakTilLosAdapterTjeneste::class.java)
    private val TRÅDNAVN = "k9-punsj-til-los"

    private fun spillAvBehandlingProsessEventer() {
        var behandlingTeller: Long = 0
        var eventTeller: Long = 0
        var forrigeOppgave: OppgaveV3? = null

        log.info("Starter avspilling av BehandlingProsessEventer")
        val tidKjøringStartet = System.currentTimeMillis()

        val behandlingsIder = punsjEventK9Repository.hentAlleDirtyEventIder()
        log.info("Fant ${behandlingsIder.size} behandlinger")

        behandlingsIder.forEach { uuid ->
            transactionalManager.transaction { tx ->
                val behandlingProsessEventer = punsjEventK9Repository.hentMedLås(tx, uuid).eventer
                behandlingProsessEventer.forEach { event -> //TODO: hva skjer om eventer kommer out of order her, fordi feks k9 har sendt i feil rekkefølge?
                    val oppgaveDto = lagOppgaveDto(event, forrigeOppgave)

                    if (event.behandlingStatus == BehandlingStatus.AVSLUTTET.kode) {
                        //val vedtaksInfo = hentVedtaksInfo()
                        //oppgaveDto = oppgaveDto.berikMedVedtaksInfo(vedtaksInfo)
                    }

                    val oppgave = oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgaveDto, tx)

                    oppgave?.let {
                        eventTeller++
                        loggFremgangForHver100(eventTeller, "Prosessert $eventTeller eventer")
                    }
                    forrigeOppgave = oppgave
                }
                forrigeOppgave = null
                behandlingTeller++
                loggFremgangForHver100(behandlingTeller, "Forsert $behandlingTeller behandlinger")

                punsjEventK9Repository.fjernDirty(uuid, tx)
            }
        }
        val (antallAlle, antallAktive) = oppgaveV3Tjeneste.tellAntall()
        val tidHeleKjøringen = System.currentTimeMillis() - tidKjøringStartet
        log.info("Antall oppgaver etter kjøring: $antallAlle, antall aktive: $antallAktive, antall nye eventer: $eventTeller fordelt på $behandlingTeller behandlinger.")
        if (eventTeller > 0) {
            log.info("Gjennomsnittstid pr behandling: ${tidHeleKjøringen / behandlingTeller}ms, Gjennsomsnittstid pr event: ${tidHeleKjøringen / eventTeller}ms")
        }
        log.info("Avspilling av BehandlingProsessEventer ferdig")
    }

    private fun loggFremgangForHver100(teller: Long, tekst: String) {
        if (teller.mod(100) == 0) {
            log.info(tekst)
        }
    }
}