package no.nav.k9.los.domene.lager.oppgave.v2

import com.fasterxml.jackson.databind.JsonNode
import no.nav.k9.los.domene.modell.FagsakYtelseType
import no.nav.k9.los.domene.modell.Fagsystem
import no.nav.k9.los.tjenester.saksbehandler.merknad.Merknad
import no.nav.k9.los.tjenester.saksbehandler.merknad.MerknadEndret
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

open class Behandling constructor(
    private val oppgaver: MutableSet<OppgaveV2> = mutableSetOf(),
    var id: Long?,
    val eksternReferanse: String,
    val fagsystem: Fagsystem,
    val ytelseType: FagsakYtelseType,
    val behandlingType: String?,
    val opprettet: LocalDateTime,
    var sistEndret: LocalDateTime?,
    val søkersId: Ident?,
    val kode6: Boolean = false,
    val skjermet: Boolean = false,
    var merknad: Merknad?,
    private val data: JsonNode?
) {
    var ferdigstilt: LocalDateTime? = null
    set(value) {
        if (ferdigstilt == null) field = value
    }

    companion object {
        private val log = LoggerFactory.getLogger(Behandling::class.java)

        fun ny(
            eksternReferanse: String,
            fagsystem: Fagsystem,
            ytelseType: FagsakYtelseType,
            behandlingType: String?,
            søkersId: Ident?,
            opprettet: LocalDateTime = LocalDateTime.now()
        ): Behandling {
            return Behandling(
                id = null,
                eksternReferanse = eksternReferanse,
                fagsystem = fagsystem,
                ytelseType = ytelseType,
                behandlingType = behandlingType,
                opprettet = opprettet,
                sistEndret = opprettet,
                søkersId = søkersId,
                kode6 = false,
                skjermet = false,
                merknad = null,
                data = null
            )
        }
    }

    fun oppgaver() = oppgaver.toSet()

    fun aktiveOppgaver() = oppgaver.filter { it.erAktiv() }
    fun ferdigstilteOppgaver() = oppgaver.filter { it.erFerdigstilt() }

    fun List<OppgaveV2>.hentOppgaveSenesteFørst(oppgaveKode: String): OppgaveV2? {
        return sortedByDescending { it.opprettet }.firstOrNull { it.oppgaveKode == oppgaveKode }
    }

    fun harAktiveOppgaver(): Boolean {
        return oppgaver.any { it.erAktiv() }
    }

    open fun erFerdigstilt(): Boolean {
        return (ferdigstilt != null).also {
            if (harAktiveOppgaver()) {
                log.warn("Behandling er satt til ferdigstilt, men har aktive oppgaver $eksternReferanse")
            }
        }
    }

    fun nyHendelse(oppgaveHendelse: OppgaveHendelse) {
        when (oppgaveHendelse) {
            is OpprettOppgave -> nyOppgave(oppgaveHendelse)
            is FerdigstillOppgave -> ferdigstillOppgave(oppgaveHendelse)
            is AvbrytOppgave -> avbrytOppgave(oppgaveHendelse)
            is FerdigstillBehandling -> ferdigstill(oppgaveHendelse)
        }
    }

    open fun ferdigstill(ferdigstillelse: Ferdigstillelse) {
        sistEndret = LocalDateTime.now()
        log.info("Ferdigstiller behandling $eksternReferanse")
        lukkAlleAktiveOppgaver(ferdigstillelse)
        if (merknad != null) { slettMerknad() }

        ferdigstilt = ferdigstillelse.tidspunkt
    }

    open fun lukkAlleAktiveOppgaver(ferdigstillelse: Ferdigstillelse?) {
        sistEndret = LocalDateTime.now()
        log.info("Lukker alle aktive oppgaver $eksternReferanse")
        aktiveOppgaver().lukkAlleMed(ferdigstillelse)
    }

    private fun ferdigstillOppgave(ferdigstillOppgave: FerdigstillOppgave) {
        if (ferdigstillOppgave.oppgaveKode == null) {
            lukkAlleAktiveOppgaver(ferdigstillOppgave)
            return
        }

        val eksisterendeAktivOppgave = aktiveOppgaver().hentOppgaveSenesteFørst(ferdigstillOppgave.oppgaveKode)
        if (eksisterendeAktivOppgave != null) {
            lukkAktiveOppgaverOpprettetFør(eksisterendeAktivOppgave, ferdigstillOppgave)
        } else {
            log.error("Ferdigstillelse inneholder oppgavekode ${ferdigstillOppgave.oppgaveKode} som ikke finnes blant aktive oppgaver. $eksternReferanse")
        }
    }

    fun avbrytOppgave(avbrytOppgave: AvbrytOppgave) {
        if (avbrytOppgave.oppgaveKode == null) {
            lukkAlleAktiveOppgaver(ferdigstillelse = null)
            return
        }

        val eksisterendeOppgave = aktiveOppgaver().hentOppgaveSenesteFørst(avbrytOppgave.oppgaveKode)
            ?: ferdigstilteOppgaver().hentOppgaveSenesteFørst(avbrytOppgave.oppgaveKode)

        eksisterendeOppgave?.run {
            sistEndret = LocalDateTime.now()
            avbrytOppgaveUtenFerdigstillelse()
        } ?: log.info("Avbrytelse inneholder oppgavekode ${avbrytOppgave.oppgaveKode} som ikke finnes blant aktive oppgaver. $eksternReferanse")
    }

    private fun lukkAktiveOppgaverOpprettetFør(eksisterendeOppgave: OppgaveV2, ferdigstillelse: Ferdigstillelse?) {
        log.info("Lukker aktive oppgaver opprettet før ${eksisterendeOppgave.oppgaveKode} $eksternReferanse")
        sistEndret = LocalDateTime.now()
        aktiveOppgaver()
            .filter { aktivOppgave -> aktivOppgave.opprettet <= eksisterendeOppgave.opprettet }
            .lukkAlleMed(ferdigstillelse)
    }

    private fun List<OppgaveV2>.lukkAlleMed(ferdigstillelse: Ferdigstillelse?) {
        if (ferdigstillelse != null) {
            forEach { it.ferdigstill(ferdigstillelse) }
        } else {
            forEach { it.avbrytOppgaveUtenFerdigstillelse() }
        }
    }

    private fun nyOppgave(opprettOppgave: OpprettOppgave) {
        if (harAktivOppgaveMedReferanseOgKode(eksternReferanse = eksternReferanse, opprettOppgave.oppgaveKode)) {
            log.warn("Har allerede eksisterende, aktiv oppgave med oppgavekode (${opprettOppgave.oppgaveKode}) på referansen. $eksternReferanse")
            return
        }

        oppgaver.add(
            OppgaveV2.ny(
                eksternReferanse = eksternReferanse,
                oppgaveKode = opprettOppgave.oppgaveKode,
                opprettet = opprettOppgave.tidspunkt,
                frist = opprettOppgave.frist
            )
        )
        sistEndret = LocalDateTime.now()
        log.info("Ny oppgave (${opprettOppgave.oppgaveKode}) lagt til $eksternReferanse")
    }

    fun harAktivOppgaveMedReferanseOgKode(eksternReferanse: String, oppgaveKode: String): Boolean {
        return oppgaver.any {
            it.erAktiv() &&
                    it.eksternReferanse == eksternReferanse &&
                    it.oppgaveKode == oppgaveKode
        }
    }

    fun lagreMerknad(merknadEndring: MerknadEndret, saksbehandlerIdent: String?) {
        if (merknadEndring.merknadKoder.isEmpty()) {
            slettMerknad()
            return
        }

        if (merknad == null) {
            merknad = merknadEndring.nyMerknad(saksbehandlerIdent, aktiveOppgaver())
            return
        }
        merknad?.oppdater(merknadEndring.merknadKoder, merknadEndring.fritekst)
    }

    private fun slettMerknad() {
        if (merknad == null)  {
            log.warn("Prøver å slette merknad som ikke finnes eller allerede er slettet")
            return
        }
        merknad?.slett()
    }
}

interface Ferdigstillelse {
    val tidspunkt: LocalDateTime
    val ansvarligSaksbehandlerIdent: String?
    val behandlendeEnhet: String?
}
