package no.nav.k9.fagsystem.k9sak

import no.nav.k9.aksjonspunktbehandling.EventTeller
import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.lager.oppgave.v2.*
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.modell.Fagsystem
import no.nav.k9.domene.modell.IModell
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.*
import org.slf4j.LoggerFactory


class K9sakEventHandlerV2(
    val oppgaveTjenesteV2: OppgaveTjenesteV2,
    val aksjonspunktHendelseMapper: AksjonspunktHendelseMapper,
) : EventTeller {
    private val log = LoggerFactory.getLogger(K9sakEventHandlerV2::class.java)

    suspend fun prosesser(hendelse: ProduksjonsstyringHendelse) {
        when (hendelse) {
            is ProduksjonsstyringDokumentHendelse -> håndterNyttDokument(hendelse)
            is ProduksjonsstyringAksjonspunktHendelse -> håndterNyttAksjonspunkt(hendelse)
            is ProduksjonsstyringBehandlingOpprettetHendelse -> håndterBehandlingOpprettet(hendelse)
            is ProduksjonsstyringBehandlingAvsluttetHendelse -> håndterBehandlingAvsluttet(hendelse)
            else -> throw UnsupportedOperationException("Mottok eventtype som ikke er støttet ${hendelse.tryggToString()}")
        }
    }

    private fun håndterBehandlingOpprettet(hendelse: ProduksjonsstyringBehandlingOpprettetHendelse) {
        log.info("Behandling opprettet hendelse: ${hendelse.behandlingType}, ${hendelse.ytelseType}, ${hendelse.saksnummer},  ${hendelse.behandlingstidFrist}, ${hendelse.tryggToString()}")

        val eksternId = hendelse.eksternId.toString()
        oppgaveTjenesteV2.nyOppgaveHendelse(
            eksternId, BehandlingEndret(
                eksternReferanse = eksternId,
                fagsystem = Fagsystem.K9SAK,
                ytelseType = FagsakYtelseType.fraKode(hendelse.ytelseType),
                behandlingType = hendelse.behandlingType,
                søkersId = Ident(id = hendelse.søkersAktørId.aktørId, Ident.IdType.AKTØRID),
                tidspunkt = hendelse.hendelseTid
            )
        )
    }

    private fun håndterBehandlingAvsluttet(hendelse: ProduksjonsstyringBehandlingAvsluttetHendelse) {
        log.info("Behandling avsluttet hendelse: ${hendelse.behandlingResultatType}, ${hendelse.tryggToString()}")

        val eksternId = hendelse.eksternId.toString()
        oppgaveTjenesteV2.nyOppgaveHendelse(eksternId,
            FerdigstillBehandling(
               tidspunkt = hendelse.hendelseTid
            )
        )
    }

    private suspend fun håndterNyttAksjonspunkt(hendelse: ProduksjonsstyringAksjonspunktHendelse) {
        log.info("Aksjonspunkthendelse: "+hendelse.aksjonspunktTilstander.joinToString(", "))

        val aksjonspunkter = hendelse.aksjonspunktTilstander.associateBy { it }
            .mapKeys { (k, _) ->
                AksjonspunktHendelseMapper.Aksjonspunkt(AksjonspunktDefinisjon.fraKode(k.aksjonspunktKode), k.status)
            }

        try {
            val nyeHendelser = aksjonspunktHendelseMapper.hentOppgavehendelser(hendelse, aksjonspunkter).toList()
            oppgaveTjenesteV2.nyeOppgaveHendelser(hendelse.eksternId.toString(), nyeHendelser)
        } catch (e: IllegalStateException) {
            log.error("Feilet ved håndtering av oppgavehendelser", e)
        }
    }

    private fun håndterNyttDokument(dokumenthendelse: ProduksjonsstyringDokumentHendelse) {
        log.warn("DOKUMENTHENDELSE er ikke implementert ${dokumenthendelse.kravdokumenter.joinToString(", ") { it.toString() }}")
    }

    override fun tellEvent(modell: IModell, oppgave: Oppgave) {
        TODO("Not yet implemented")
    }
}
