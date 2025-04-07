package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj

import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.repository.OppgaveKøRepository
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.domene.repository.ReservasjonRepository
import no.nav.k9.los.domene.repository.StatistikkRepository
import no.nav.k9.los.domene.lager.oppgave.v2.*
import no.nav.k9.los.nyoppgavestyring.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHandlerMetrics
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventTeller
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.IModell
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.punsjtillos.K9PunsjTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.ko.KøpåvirkendeHendelse
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveHendelseMottatt
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.query.db.EksternOppgaveId
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.AlleOppgaverNyeOgFerdigstilte
import no.nav.k9.los.tjenester.saksbehandler.oppgave.ReservasjonTjeneste
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.OpentelemetrySpanUtil
import org.slf4j.LoggerFactory


class K9PunsjEventHandler constructor(
    private val oppgaveRepository: OppgaveRepository,
    private val punsjEventK9Repository: K9PunsjEventRepository,
    private val punsjTilLosAdapterTjeneste: K9PunsjTilLosAdapterTjeneste,
    private val køpåvirkendeHendelseChannel: Channel<KøpåvirkendeHendelse>,
) : EventTeller {
    private val log = LoggerFactory.getLogger(K9PunsjEventHandler::class.java)

    companion object {
        private val typer = BehandlingType.values().filter { it.kodeverk == "PUNSJ_INNSENDING_TYPE" }
    }

    @WithSpan
    fun prosesser(event: PunsjEventDto) {
        EventHandlerMetrics.time("k9punsj", "gjennomført") {
            log.info(event.safePrint())
            val modell = punsjEventK9Repository.lagre(event = event)
            val oppgave = modell.oppgave()
            oppgaveRepository.lagre(oppgave.eksternId) {
                tellEvent(modell, oppgave)
                oppgave
            }

            OpentelemetrySpanUtil.span("punsjTilLosAdapterTjeneste.oppdaterOppgaveForEksternId") {
                punsjTilLosAdapterTjeneste.oppdaterOppgaveForEksternId(
                    event.eksternId
                )
            }

            runBlocking {
                køpåvirkendeHendelseChannel.send(
                    OppgaveHendelseMottatt(
                        Fagsystem.PUNSJ,
                        EksternOppgaveId("K9", event.eksternId.toString())
                    )
                )
            }
        }
    }
}
