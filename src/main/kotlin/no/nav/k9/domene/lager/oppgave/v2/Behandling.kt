package no.nav.k9.domene.lager.oppgave.v2

import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.modell.Fagsystem
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
    val kode6: Boolean,
    val skjermet: Boolean,
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
                skjermet = false
            )
        }
    }

    constructor(other: Behandling, id: Long? = null) : this(
        id = id,
        oppgaver = other.oppgaver.toMutableSet(),
        eksternReferanse = other.eksternReferanse,
        fagsystem = other.fagsystem,
        ytelseType = other.ytelseType,
        behandlingType = other.behandlingType,
        opprettet = other.opprettet,
        sistEndret = other.sistEndret,
        søkersId = other.søkersId,
        kode6 = other.kode6,
        skjermet = other.skjermet
    )

    fun oppgaver() = oppgaver.toSet()

    fun aktiveOppgaver() = oppgaver.filter { it.erAktiv() }

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
            is AvbrytOppgave -> lukkAktiveOppgaverFørOppgittOppgavekode(oppgaveKode = oppgaveHendelse.oppgaveKode)
            is FerdigstillBehandling -> ferdigstill(oppgaveHendelse)
        }
    }

    open fun ferdigstill(ferdigstillelse: Ferdigstillelse) {
        sistEndret = LocalDateTime.now()
        log.info("Ferdigstiller behandling $eksternReferanse")
        lukkAktiveOppgaver(ferdigstillelse)
        ferdigstilt = ferdigstillelse.tidspunkt
    }

    open fun lukkAktiveOppgaver(ferdigstillelse: Ferdigstillelse?) {
        sistEndret = LocalDateTime.now()
        log.info("Lukker alle aktive oppgaver $eksternReferanse")
        aktiveOppgaver().lukkAlleMed(ferdigstillelse)
    }

    private fun ferdigstillOppgave(ferdigstillOppgave: FerdigstillOppgave) {
        lukkAktiveOppgaverFørOppgittOppgavekode(ferdigstillOppgave.oppgaveKode, ferdigstillOppgave)
    }

    fun lukkAktiveOppgaverFørOppgittOppgavekode(oppgaveKode: String?, ferdigstillelse: Ferdigstillelse? = null) {
        if (oppgaveKode == null) {
            lukkAktiveOppgaver(ferdigstillelse)
            return
        }

        val aktiveOppgaver = aktiveOppgaver()
        val ferdigstiltOppgaveOpprettet = aktiveOppgaver.firstOrNull { it.oppgaveKode == oppgaveKode }?.opprettet
        if (ferdigstiltOppgaveOpprettet == null) {
            log.error("Ferdigstillelse inneholder oppgavekode ${oppgaveKode} som ikke finnes blant aktive oppgaver. $eksternReferanse")
        } else {
            log.info("Lukker aktive oppgaver opprettet før ${oppgaveKode} $eksternReferanse")
            sistEndret = LocalDateTime.now()
            aktiveOppgaver
                .filter { aktivOppgave -> aktivOppgave.opprettet <= ferdigstiltOppgaveOpprettet }
                .lukkAlleMed(ferdigstillelse)
        }
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
}

interface Ferdigstillelse {
    val tidspunkt: LocalDateTime
    val ansvarligSaksbehandlerIdent: String?
    val behandlendeEnhet: String?
}

