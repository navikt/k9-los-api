package no.nav.k9.domene.lager.oppgave.v2

import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.modell.Fagsystem
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

open class Behandling constructor(
    private val oppgaver: MutableSet<Deloppgave> = mutableSetOf(),
    val id: UUID,
    val eksternReferanse: String,
    val fagsystem: Fagsystem,
    val ytelseType: FagsakYtelseType,
    val behandlingType: String?,
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

        fun ny(eksternReferanse: String, fagsystem: Fagsystem, ytelseType: FagsakYtelseType, behandlingType: String?, søkersId: Ident?): Behandling {
            return Behandling(
                id = UUID.randomUUID(),
                eksternReferanse = eksternReferanse,
                fagsystem = fagsystem,
                ytelseType = ytelseType,
                behandlingType = behandlingType,
                søkersId = søkersId,
                kode6 = false,
                skjermet = false
            )
        }
    }

    constructor(other: Behandling, id: UUID = UUID.randomUUID()) : this(
        id = id,
        oppgaver = other.oppgaver.toMutableSet(),
        eksternReferanse = other.eksternReferanse,
        fagsystem = other.fagsystem,
        ytelseType = other.ytelseType,
        behandlingType = other.behandlingType,
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
                log.warn("Behandling er satt til ferdigstilt, men har aktive deloppgaver $eksternReferanse")
            }
        }
    }

    open fun ferdigstill(ferdigstillelse: Ferdigstillelse) {
        log.info("Ferdigstiller behandling $eksternReferanse")
        lukkAktiveOppgaver(ferdigstillelse)
        ferdigstilt = ferdigstillelse.tidspunkt
    }

    open fun lukkAktiveOppgaver(ferdigstillelse: Ferdigstillelse) {
        log.info("Lukker oppgaver $eksternReferanse")
        oppgaver.filter { it.erAktiv() }.forEach { it.ferdigstill(ferdigstillelse)}
    }

    fun settPåVent() {
        log.info("Setter alle oppgaver på vent $eksternReferanse")
        oppgaver.filter { it.erAktiv() }.forEach { it.avbrytOppgaveUtenFerdigstillelse() }
    }

    fun nyOppgave(opprettOppgave: OpprettOppgave) {
        log.info("Ny oppgave (${opprettOppgave.oppgaveKode}) lagt til $eksternReferanse")
        oppgaver.add(
            Deloppgave.ny(
                eksternReferanse = eksternReferanse,
                oppgaveKode = opprettOppgave.oppgaveKode,
                opprettet = opprettOppgave.tidspunkt,
                frist = opprettOppgave.frist
            )
        )
    }
}
