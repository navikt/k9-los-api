package no.nav.k9.los.aksjonspunktbehandling

import no.nav.k9.klage.kodeverk.behandling.oppgavetillos.EventHendelse
import no.nav.k9.klage.kontrakt.behandling.oppgavetillos.KlagebehandlingProsessHendelse
import no.nav.k9.los.domene.modell.BehandlingStatus
import no.nav.k9.los.domene.modell.K9KlageModell
import no.nav.k9.los.domene.repository.BehandlingProsessEventKlageRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.klagetillos.K9KlageTilLosAdapterTjeneste
import org.slf4j.LoggerFactory


class K9KlageEventHandler constructor(
    private val behandlingProsessEventKlageRepository: BehandlingProsessEventKlageRepository,
    private val k9KlageTilLosAdapterTjeneste: K9KlageTilLosAdapterTjeneste,
) {
    private val log = LoggerFactory.getLogger(K9KlageEventHandler::class.java)

    fun prosesser(
        eventInn: KlagebehandlingProsessHendelse
    ) {
        val event = håndterVaskeevent(eventInn)
        if (event == null) return

        behandlingProsessEventKlageRepository.lagre(event.eksternId!!) { k9KlageModell ->
            if (k9KlageModell == null) {
                return@lagre K9KlageModell(mutableListOf(event))
            }
            if (k9KlageModell.eventer.contains(event)) {
                log.info("""Skipping eventen har kommet tidligere ${event.eventTid}""")
                return@lagre k9KlageModell
            }
            k9KlageModell.eventer.add(event)
            k9KlageModell
        }
        k9KlageTilLosAdapterTjeneste.oppdaterOppgaveForBehandlingUuid(event.eksternId)
    }

    fun håndterVaskeevent(event: KlagebehandlingProsessHendelse): KlagebehandlingProsessHendelse? {
        if (event.eventHendelse != EventHendelse.VASKEEVENT) {
            return event
        }

        // Gjøres utenfor transaksjon fordi den ikke muterer data.
        // Det er ikke mulig å unngå lagring selv om eventet skal ignoreres hvis den skulle ha vært i samme transaksjon (pga on conflict(id) update.. ) i lagre-metoden
        val eksisterendeEventModell = behandlingProsessEventKlageRepository.hent(event.eksternId!!)
        if (eksisterendeEventModell.eventer.any { tidligereEvent -> tidligereEvent.behandlingStatus == BehandlingStatus.AVSLUTTET.kode }) {
            return null
        }

        if (eksisterendeEventModell.eventer.isEmpty()) {
            log.info("Vaskeeventfiltrering gjelder behandling som ikke tidligere finnes i los ${event.eksternId}")
            return event
        }

        log.info("Vaskeeventfiltrering ${event.behandlingStatus} - ${event.eventTid} - ${event.eksternId}")
        return eksisterendeEventModell.eventer.last().eventTid
            .takeIf { sisteEventTid -> sisteEventTid.isAfter(event.eventTid) }
            ?.let { sisteEventTid -> KlagebehandlingProsessHendelse.builder()
                .medEksternId(event.eksternId)
                .medPåklagdBehandlingEksternId(event.påklagdBehandlingEksternId)
                .medFagsystem(event.fagsystem)
                .medSaksnummer(event.saksnummer)
                .medAktørId(event.aktørId)
                .medEventTid(sisteEventTid.plusNanos(100_1000))
                .getBehandlingstidFrist(event.behandlingstidFrist)
                .medEventHendelse(event.eventHendelse)
                .medBehandlingStatus(event.behandlingStatus)
                .medBehandlingSteg(event.behandlingSteg)
                .medBehandlendeEnhet(event.behandlendeEnhet)
                .medYtelseTypeKode(event.ytelseTypeKode)
                .medBehandlingResultat(event.resultatType)
                .medBehandlingTypeKode(event.behandlingTypeKode)
                .medOpprettetBehandling(event.opprettetBehandling)
                .medAnsvarligSaksbehandler(event.ansvarligSaksbehandler)
                .medFagsakPeriode(event.fagsakPeriode)
                .medPleietrengendeAktørId(event.pleietrengendeAktørId)
                .medRelatertPartAktørId(event.relatertPartAktørId)
                .medAnsvarligBeslutter(event.ansvarligBeslutter)
                .medAksjonspunktTilstander(event.aksjonspunkttilstander)
                .medVedtaksdato(event.vedtaksdato)
                .build()
            } ?: event
    }
}
