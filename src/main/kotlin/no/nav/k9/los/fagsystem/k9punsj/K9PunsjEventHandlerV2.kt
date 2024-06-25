package no.nav.k9.los.fagsystem.k9punsj

import no.nav.k9.los.aksjonspunktbehandling.EventTeller
import no.nav.k9.los.aksjonspunktbehandling.EventHandlerMetrics
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.lager.oppgave.v2.*
import no.nav.k9.los.domene.modell.FagsakYtelseType
import no.nav.k9.los.domene.modell.Fagsystem
import no.nav.k9.los.domene.modell.IModell
import no.nav.k9.los.fagsystem.k9punsj.kontrakt.ProduksjonsstyringHendelse
import no.nav.k9.los.fagsystem.k9punsj.kontrakt.ProduksjonsstyringOppgaveAvbruttHendelse
import no.nav.k9.los.fagsystem.k9punsj.kontrakt.ProduksjonsstyringOppgaveFerdigstiltHendelse
import no.nav.k9.los.fagsystem.k9punsj.kontrakt.ProduksjonsstyringOppgaveOpprettetHendelse
import no.nav.k9.los.integrasjon.azuregraph.IAzureGraphService
import org.slf4j.LoggerFactory


class K9PunsjEventHandlerV2(
    val oppgaveTjenesteV2: OppgaveTjenesteV2,
    val azureGraphService: IAzureGraphService,
) : EventTeller {
    private val log = LoggerFactory.getLogger(K9PunsjEventHandlerV2::class.java)

    suspend fun prosesser(hendelse: ProduksjonsstyringHendelse) {
        when (hendelse) {
            is ProduksjonsstyringOppgaveOpprettetHendelse -> håndterBehandlingOpprettet(hendelse)
            is ProduksjonsstyringOppgaveFerdigstiltHendelse -> håndterBehandlingFerdigstilt(hendelse)
            is ProduksjonsstyringOppgaveAvbruttHendelse -> håndterBehandlingAvbrutt(hendelse)
            else -> throw UnsupportedOperationException("Mottok eventtype som ikke er støttet ${hendelse.safeToString()}")
        }
    }

    private fun håndterBehandlingOpprettet(hendelse: ProduksjonsstyringOppgaveOpprettetHendelse) {
        log.info("Oppgave opprettet hendelse: ${hendelse.safeToString()}")
        val eksternId = hendelse.eksternId.toString()
        EventHandlerMetrics.time("k9punsj", "behandlingOpprettet") {
            oppgaveTjenesteV2.nyOppgaveHendelse(
                eksternId, BehandlingEndret(
                    eksternReferanse = eksternId,
                    fagsystem = Fagsystem.K9SAK,
                    ytelseType = hendelse.ytelseType?.let { FagsakYtelseType.fraKode(it) } ?: FagsakYtelseType.UKJENT,
                    behandlingType = hendelse.behandlingType.toString(),
                    søkersId = hendelse.søkersAktørId?.aktørId?.let { Ident(id = it, Ident.IdType.AKTØRID) },
                    tidspunkt = hendelse.hendelseTid
                )
            )
        }
    }

    private suspend fun håndterBehandlingFerdigstilt(hendelse: ProduksjonsstyringOppgaveFerdigstiltHendelse) {
        log.info("Oppgave ferdigstilt hendelse: ${hendelse.safeToString()}")
        EventHandlerMetrics.timeSuspended("k9pusj", "behandlingAvbrutt") {
            try {
                val eksternId = hendelse.eksternId.toString()
                oppgaveTjenesteV2.nyOppgaveHendelse(eksternId, FerdigstillBehandling(
                        tidspunkt = hendelse.hendelseTid,
                        behandlendeEnhet = hendelse.ferdigstiltAv?.let {
                            azureGraphService.hentEnhetForBrukerMedSystemToken(it)
                        } ?: "UKJENT",
                        ansvarligSaksbehandlerIdent = hendelse.ferdigstiltAv
                    )
                )
            } catch (e: IllegalStateException) {
                log.error("Feilet ved håndtering av oppgave ferdigstilt hendelse", e)
            }
        }
    }


    private fun håndterBehandlingAvbrutt(hendelse: ProduksjonsstyringOppgaveAvbruttHendelse) {
        log.info("Oppgave avbrutt hendelse: ${hendelse.safeToString()}")
        EventHandlerMetrics.time("k9pusj", "behandlingAvbrutt") {
            try {
                val eksternId = hendelse.eksternId.toString()
                oppgaveTjenesteV2.nyOppgaveHendelse(
                    eksternId,
                    AvbrytOppgave(
                        tidspunkt = hendelse.hendelseTid,
                        oppgaveKode = null
                    )
                )
            } catch (e: IllegalStateException) {
                log.error("Feilet ved håndtering av oppgave avbrutt hendelse", e)
            }
        }
    }


    override fun tellEvent(modell: IModell, oppgave: Oppgave) {
        TODO("Not yet implemented")
    }
}
