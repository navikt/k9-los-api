package no.nav.k9.aksjonspunktbehandling.k9sak

import no.nav.k9.aksjonspunktbehandling.EventTeller
import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.lager.oppgave.v2.Behandling
import no.nav.k9.domene.lager.oppgave.v2.Ferdigstillelse
import no.nav.k9.domene.lager.oppgave.v2.Ident
import no.nav.k9.domene.lager.oppgave.v2.OppgaveTjenesteV2
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
        val eksternId = hendelse.eksternId.toString()
        oppgaveTjenesteV2.opprettBehandling(eksternId) {
            Behandling.ny(
                eksternReferanse = eksternId,
                fagsystem = Fagsystem.K9SAK,
                ytelseType = FagsakYtelseType.fraKode(hendelse.ytelseType),
                behandlingType = hendelse.behandlingType,
                søkersId = Ident(id = hendelse.søkersAktørId.aktørId, Ident.IdType.AKTØRID)
            )
        }
    }

    private fun håndterBehandlingAvsluttet(hendelse: ProduksjonsstyringBehandlingAvsluttetHendelse) {
        val eksternId = hendelse.eksternId.toString()
        oppgaveTjenesteV2.avsluttBehandling(eksternId,
            Ferdigstillelse(
               tidspunkt = hendelse.hendelseTid
            )
        )
    }

    private suspend fun håndterNyttAksjonspunkt(hendelse: ProduksjonsstyringAksjonspunktHendelse) {
        val aksjonspunkter = hendelse.aksjonspunktTilstander.associateBy { it }
            .mapKeys { (k, _) ->
                AksjonspunktHendelseMapper.Aksjonspunkt(AksjonspunktDefinisjon.fraKode(k.aksjonspunktKode), k.status)
            }

        try {
            aksjonspunktHendelseMapper.hentOppgavehendelser(hendelse, aksjonspunkter).forEach {
                oppgaveTjenesteV2.nyOppgaveHendelse(hendelse.eksternId.toString(), it)
            }
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
