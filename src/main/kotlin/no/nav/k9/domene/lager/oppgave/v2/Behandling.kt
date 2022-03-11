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
    val sistEndret: LocalDateTime?,
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
            is FerdigstillOppgave -> lukkAktiveOppgaverFørOppgittOppgavekode(oppgaveHendelse)
            is FerdigstillBehandling -> ferdigstill(oppgaveHendelse)
        }
    }

    open fun ferdigstill(ferdigstillelse: Ferdigstillelse) {
        log.info("Ferdigstiller behandling $eksternReferanse")
        lukkAktiveOppgaver(ferdigstillelse)
        ferdigstilt = ferdigstillelse.tidspunkt
    }

    open fun lukkAktiveOppgaver(ferdigstillelse: Ferdigstillelse) {
        log.info("Lukker alle aktive oppgaver $eksternReferanse")
        return oppgaver.filter { it.erAktiv() }.forEach { it.ferdigstill(ferdigstillelse) }
    }

    open fun lukkAktiveOppgaverFørOppgittOppgavekode(ferdigstillelse: FerdigstillOppgave) {
        return oppgaver.filter { it.erAktiv() }.let { aktiveOppgaver ->
            if (ferdigstillelse.oppgaveKode != null) {
                log.info("Lukker aktive oppgaver opprettet før ${ferdigstillelse.oppgaveKode} $eksternReferanse")
                val ferdigstiltOppgaveOpprettet =
                    aktiveOppgaver.first { it.oppgaveKode == ferdigstillelse.oppgaveKode }.opprettet
                aktiveOppgaver.filter { aktiveOppgave -> aktiveOppgave.opprettet <= ferdigstiltOppgaveOpprettet }
                    .forEach { it.ferdigstill(ferdigstillelse) }
            } else {
                log.info("Lukker alle aktive oppgaver $eksternReferanse")
                aktiveOppgaver.forEach { it.ferdigstill(ferdigstillelse) }
            }
        }
    }

    fun settPåVent() {
        log.info("Setter alle oppgaver på vent $eksternReferanse")
        oppgaver.filter { it.erAktiv() }.forEach { it.avbrytOppgaveUtenFerdigstillelse() }
    }


    fun nyOppgave(opprettOppgave: OpprettOppgave) {
        if (harAktivOppgaveMedReferanseOgKode(eksternReferanse = eksternReferanse, opprettOppgave.oppgaveKode)) {
            log.warn("Har allerede eksisterende, aktiv oppgave med oppgavekode på referansen. $eksternReferanse")
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

