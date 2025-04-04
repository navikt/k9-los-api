package no.nav.k9.los.fagsystem.k9sak

import no.nav.k9.los.nyoppgavestyring.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.los.domene.lager.oppgave.v2.AvbrytOppgave
import no.nav.k9.los.domene.lager.oppgave.v2.FerdigstillOppgave
import no.nav.k9.los.domene.lager.oppgave.v2.OppgaveHendelse
import no.nav.k9.los.domene.lager.oppgave.v2.OpprettOppgave
import no.nav.k9.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.ProduksjonsstyringAksjonspunktHendelse
import org.slf4j.LoggerFactory

class AksjonspunktHendelseMapper(
    val azureGraphService: IAzureGraphService,
) {
    private val log = LoggerFactory.getLogger(AksjonspunktHendelseMapper::class.java)

    suspend fun hentOppgavehendelser(
        hendelse: ProduksjonsstyringAksjonspunktHendelse,
        aksjonspunkter: Map<Aksjonspunkt, AksjonspunktTilstandDto>
    ): List<OppgaveHendelse> {
        return aksjonspunkter
            .filter { it.key.erOppgave() }
            .map { (aksjonspunkt, dto) ->
                opprettOppgaveHendelse(hendelse, aksjonspunkt, dto) }
            .filterNotNull()
    }

    suspend fun opprettOppgaveHendelse(
        hendelse: ProduksjonsstyringAksjonspunktHendelse,
        aksjonspunkt: Aksjonspunkt,
        dto: AksjonspunktTilstandDto
    ): OppgaveHendelse? {
        if (aksjonspunkt.erAktiv()) {
                return OpprettOppgave(
                    tidspunkt = hendelse.hendelseTid,
                    oppgaveKode = dto.aksjonspunktKode,
                    frist = dto.fristTid
                )
        }
        if (aksjonspunkt.erUtført()) {
            val behandlendeEnhet = dto.ansvarligSaksbehandler?.let {
                azureGraphService.hentEnhetForBrukerMedSystemToken(it) } ?:
                "UKJENT".also {
                    log.warn("Forventet saksbehandler satt for $dto. Bruker 'UKJENT' som enhet")
                }

            return FerdigstillOppgave(
                tidspunkt = hendelse.hendelseTid,
                ansvarligSaksbehandlerIdent = dto.ansvarligSaksbehandler,
                behandlendeEnhet = behandlendeEnhet,
                oppgaveKode = dto.aksjonspunktKode
            )
        }
        if (aksjonspunkt.erLukket()) {
            return AvbrytOppgave(
                tidspunkt = hendelse.hendelseTid,
                oppgaveKode = dto.aksjonspunktKode
            )
        }
        return null
    }

    fun hentVentehendelser(
        hendelse: ProduksjonsstyringAksjonspunktHendelse,
        aksjonspunkt: Aksjonspunkt,
        dto: AksjonspunktTilstandDto
    ) {
        if (aksjonspunkt.erAktiv()) {
            log.info("Satt på vent med årsak: ${dto.venteårsak}, ${hendelse.eksternId}.")
        } else {
            log.info("Ikke lenger på vent. ${hendelse.eksternId}")
        }
    }

    data class Aksjonspunkt(
        val aksjonspunktDefinisjon: AksjonspunktDefinisjon,
        val status: AksjonspunktStatus
    ) {
        fun erAktiv(): Boolean {
            return status == AksjonspunktStatus.OPPRETTET
        }

        fun erLukket(): Boolean {
            return status == AksjonspunktStatus.UTFØRT ||
                    status == AksjonspunktStatus.AVBRUTT
        }

        fun erUtført(): Boolean {
            return status == AksjonspunktStatus.UTFØRT
        }

        fun erOppgave(): Boolean {
            return !aksjonspunktDefinisjon.erAutopunkt()
        }

        fun erAutomatiskBehandlet(): Boolean {
            return aksjonspunktDefinisjon.erAutopunkt()
        }
    }
}