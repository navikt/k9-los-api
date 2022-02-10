package no.nav.k9.fagsystem.k9sak

import no.nav.k9.domene.lager.oppgave.v2.Behandling
import no.nav.k9.domene.lager.oppgave.v2.Ident
import no.nav.k9.domene.lager.oppgave.v2.OppgaveRepositoryV2
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.modell.Fagsystem
import no.nav.k9.domene.modell.aktiveAksjonspunkt
import no.nav.k9.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.kodeverk.behandling.BehandlingStatus
import org.slf4j.LoggerFactory
import java.lang.IllegalStateException
import java.util.*

class OppgaveTjenesteSak(
    val oppgaveRepositoryV2: OppgaveRepositoryV2,
    val k9SakRepository: K9SakRepository,
) {
    private val log = LoggerFactory.getLogger(OppgaveTjenesteSak::class.java)

    fun hentBehandling(referanse: UUID) : FagsystemBehandling? {
        return oppgaveRepositoryV2.hentBehandling(referanse)?.run {
            log.info("Henter behandling fra db $eksternReferanse")
            val k9sak = k9SakRepository.hentFagsystemData(referanse)
            FagsystemBehandling(this, k9sak)
        }
    }

    fun hentAlleAktiveOppgaverForFagsystem(): List<FagsystemBehandling> {
        val aktiveBehandlinger = oppgaveRepositoryV2.hentAlleAktiveOppgaverForFagsystemGruppertPrReferanse(Fagsystem.K9SAK)
        val behandlingK9Sak = k9SakRepository.hentSaksbehandlingForAlle(aktiveBehandlinger.keys)
        return aktiveBehandlinger.map { (k, v) -> FagsystemBehandling(v, behandlingK9Sak[k]) }
    }

    fun nyHendelse(event: BehandlingProsessEventDto) {
        val behandling = hentBehandling(event.eksternId!!) ?: event.lagK9SakModell()

        log.info("Oppdaterer fagsystembehandling ${event.eksternId} med nytt prosessevent")
        val eventResultat = event.aktiveAksjonspunkt().eventResultat()

        val manuelleAktiveAksjonspunkter = event.aktiveAksjonspunkt().manuelleAksjonspunkter()
        manuelleAktiveAksjonspunkter.forEach { manueltAksjonspunkt ->
            behandling.nyOppgave(event.eventTid, manueltAksjonspunkt.key.kode)
        }

        if (eventResultat.setterOppgavePåVent()) {
            behandling.settPåVent()
        } else if (eventResultat.lukkerOppgave()) {
            behandling.lukkAktiveOppgaver(
                event.eventTid,
                ansvarligSaksbehandler = event.ansvarligSaksbehandlerIdent,
                enhet = event.behandlendeEnhet
            )
        }
        if (event.behandlingStatus == BehandlingStatus.AVSLUTTET.kode) {
            behandling.ferdigstill(
                event.eventTid,
                ansvarligSaksbehandler = event.ansvarligSaksbehandlerIdent,
                enhet = event.behandlendeEnhet
            )
        }
        oppgaveRepositoryV2.lagre(behandling)
    }

    private fun BehandlingProsessEventDto.lagK9SakModell(): FagsystemBehandling {
        val eksternReferanse = eksternId ?: throw IllegalStateException("Mangler ekstern referanse")
        log.info("Oppretter ny fagsystemBehandling $eksternReferanse")

        return FagsystemBehandling(
            behandling = nyBehandling(kode6 = false, skjermet = false),
            fagsystemBehandlingdata = FagsystemBehandlingData.opprettFra(eksternReferanse, this)
        )
    }

    private fun BehandlingProsessEventDto.nyBehandling(kode6: Boolean, skjermet: Boolean): Behandling {
        log.info("Oppretter ny behandling $eksternId")
        return Behandling(
            id = UUID.randomUUID(),
            eksternReferanse = this.eksternId.toString(),
            fagsystem = Fagsystem.K9SAK,
            ytelseType = FagsakYtelseType.fraKode(this.ytelseTypeKode),
            søkersId = Ident(this.aktørId, Ident.IdType.AKTØRID),
            kode6 = kode6,
            skjermet = skjermet
        )
    }
}