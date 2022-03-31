package no.nav.k9.fagsystem.k9punsj

import no.nav.k9.aksjonspunktbehandling.EventTeller
import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.lager.oppgave.v2.*
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.modell.Fagsystem
import no.nav.k9.domene.modell.IModell
import no.nav.k9.fagsystem.k9punsj.kontrakt.ProduksjonsstyringHendelse
import no.nav.k9.fagsystem.k9punsj.kontrakt.ProduksjonsstyringOppgaveAvbruttHendelse
import no.nav.k9.fagsystem.k9punsj.kontrakt.ProduksjonsstyringOppgaveFerdigstiltHendelse
import no.nav.k9.fagsystem.k9punsj.kontrakt.ProduksjonsstyringOppgaveOpprettetHendelse
import no.nav.k9.fagsystem.k9sak.AksjonspunktHendelseMapper
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.ProduksjonsstyringAksjonspunktHendelse
import org.slf4j.LoggerFactory


class K9PunsjEventHandlerV2(
    val oppgaveTjenesteV2: OppgaveTjenesteV2,
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
        log.info("Behandling opprettet hendelse: ${hendelse.safeToString()}")

        val eksternId = hendelse.eksternId.toString()
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

    private fun håndterBehandlingFerdigstilt(hendelse: ProduksjonsstyringOppgaveFerdigstiltHendelse) {
        log.info("Behandling ferdigstilt hendelse: ${hendelse.safeToString()}")

        try {
            val eksternId = hendelse.eksternId.toString()
            oppgaveTjenesteV2.nyOppgaveHendelse(eksternId,
                FerdigstillBehandling(
                   tidspunkt = hendelse.hendelseTid
                )
            )
        } catch (e: IllegalStateException) {
            log.error("Feilet ved håndtering av behandlingavsluttet hendelse", e)
        }
    }


    private fun håndterBehandlingAvbrutt(hendelse: ProduksjonsstyringOppgaveAvbruttHendelse) {
        log.info("Behandling avbrutt hendelse: ${hendelse.safeToString()}")

//        try {
//            val eksternId = hendelse.eksternId.toString()
//            oppgaveTjenesteV2.nyOppgaveHendelse(eksternId,
//                AvbruttOppgave(
//                    tidspunkt = hendelse.hendelseTid
//                )
//            )
//        } catch (e: IllegalStateException) {
//            log.error("Feilet ved håndtering av behandlingavsluttet hendelse", e)
//        }
    }


    override fun tellEvent(modell: IModell, oppgave: Oppgave) {
        TODO("Not yet implemented")
    }
}
