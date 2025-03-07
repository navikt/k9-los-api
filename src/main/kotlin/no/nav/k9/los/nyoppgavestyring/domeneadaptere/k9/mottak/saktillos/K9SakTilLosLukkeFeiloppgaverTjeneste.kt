package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.saktillos

import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.kodeverk.behandling.BehandlingStatus
import no.nav.k9.kodeverk.behandling.FagsakYtelseType
import no.nav.k9.los.Configuration
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.k9sakberiker.K9SakBerikerInterfaceKludge
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveFeltverdiDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*
import kotlin.concurrent.thread

class K9SakTilLosLukkeFeiloppgaverTjeneste(
    private val behandlingProsessEventK9Repository: K9SakEventRepository,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val config: Configuration,
    private val transactionalManager: TransactionalManager,
    private val k9SakBerikerKlient: K9SakBerikerInterfaceKludge,
) {

    private val log: Logger = LoggerFactory.getLogger(K9SakTilLosLukkeFeiloppgaverTjeneste::class.java)
    private val TRÅDNAVN = "k9-sak-til-los-lukke-feiloppgaver"

    fun kjørFeiloppgaverVask() {
        if (config.nyOppgavestyringAktivert()) {
            log.info("Starter vask av oppgaver mot eksisterende behandlinger i k9sak")
            thread(
                start = true,
                isDaemon = true,
                name = TRÅDNAVN
            ) {
                spillAvBehandlingProsessEventer()
            }
        } else log.info("Ny oppgavestyring er deaktivert")
    }

    private fun spillAvBehandlingProsessEventer() {
        log.info("Starter avspilling av historiske BehandlingProsessEventer")


        val åpneOppgaver = transactionalManager.transaction {
            oppgaveV3Tjeneste.hentEksternIdForOppgaverMedStatus("k9sak", "K9", Oppgavestatus.VENTER, it)
        }

        log.info("Fant ${åpneOppgaver.size} åpne oppgaver")

        var oppgaveteller: Long = 0
        var lukketOppgaveteller: Long = 0
        åpneOppgaver.forEach { uuid ->
            transactionalManager.transaction { tx ->
                val aktivOppgave = oppgaveV3Tjeneste.hentAktivOppgave(uuid, "k9sak", "K9", tx)
                val behandling = k9SakBerikerKlient.hentBehandling(UUID.fromString(aktivOppgave.eksternId))
                if (behandling == null || behandling.sakstype == FagsakYtelseType.OBSOLETE) {
                    val nå = LocalDateTime.now()
                    val oppgaveDto = OppgaveDto(aktivOppgave)
                    val oppgaveLukket = oppgaveDto
                        .copy(
                            status = Oppgavestatus.LUKKET.kode,
                            versjon = nå.toString(),
                            endretTidspunkt = nå
                        )
                        .erstattFeltverdi(
                            OppgaveFeltverdiDto(
                                nøkkel = "ytelsestype",
                                verdi = FagsakYtelseType.OBSOLETE.kode
                            )
                        )
                        .erstattFeltverdi(
                            OppgaveFeltverdiDto(
                                nøkkel = "resultattype",
                                verdi = BehandlingResultatType.HENLAGT_FEILOPPRETTET.kode
                            )
                        )
                        .erstattFeltverdi(
                            OppgaveFeltverdiDto(
                                nøkkel = "behandlingsstatus",
                                verdi = BehandlingStatus.AVSLUTTET.kode
                            )
                        )

                    //lukk oppgave
                    oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgaveLukket, tx)
                    lukketOppgaveteller++
                }
            }
            oppgaveteller++
            loggFremgangForHver100(oppgaveteller, "Lukking håndert for $oppgaveteller oppgaver")
        }

        log.info("Antall oppgaver lukket av vaskejobb (k9-sak): $lukketOppgaveteller")
        log.info("Lukke feilsaker k9sak ferdig")

        behandlingProsessEventK9Repository.nullstillHistorikkvask()
    }

    private fun loggFremgangForHver100(teller: Long, tekst: String) {
        if (teller.mod(100) == 0) {
            log.info(tekst)
        }
    }
}
