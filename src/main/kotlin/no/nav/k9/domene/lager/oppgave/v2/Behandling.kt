package no.nav.k9.domene.lager.oppgave.v2

import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.modell.Fagsystem
import no.nav.k9.fagsystem.k9sak.FagsystemBehandling
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

open class Behandling constructor(
    private val oppgaver: MutableSet<OppgaveV2> = mutableSetOf(),
    val id: UUID,
    val eksternReferanse: String,
    val fagsystem: Fagsystem,
    val ytelseType: FagsakYtelseType,
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
    }

    constructor(other: Behandling, id: UUID = UUID.randomUUID()) : this(
        id = id,
        oppgaver = other.oppgaver.toMutableSet(),
        eksternReferanse = other.eksternReferanse,
        fagsystem = other.fagsystem,
        ytelseType = other.ytelseType,
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

    open fun ferdigstill(
        tidspunkt: LocalDateTime = LocalDateTime.now(),
        ansvarligSaksbehandler: String? = null,
        enhet: String? = null
    ) {
        log.info("Ferdigstiller behandling $eksternReferanse")
        lukkAktiveOppgaver(tidspunkt, ansvarligSaksbehandler = ansvarligSaksbehandler, enhet = enhet)
        ferdigstilt = tidspunkt
    }

    open fun lukkAktiveOppgaver(
        tidspunkt: LocalDateTime = LocalDateTime.now(),
        ansvarligSaksbehandler: String? = null,
        enhet: String? = null
    ) {
        log.info("Lukker oppgaver $eksternReferanse")
        oppgaver.filter { it.erAktiv() }.forEach { it.ferdigstill(
            tidspunkt = tidspunkt,
            ansvarligSaksbehandler = ansvarligSaksbehandler,
            behandlendeEnhet = enhet
        )}
    }

    fun settPåVent() {
        log.info("Setter alle oppgaver på vent $eksternReferanse")
        oppgaver.filter { it.erAktiv() }.forEach { it.avbrytOppgaveUtenFerdigstillelse() }
    }

    fun nyOppgave(
        opprettet: LocalDateTime,
        oppgaveKode: String
    ) {
        log.info("Ny oppgave lagt til $eksternReferanse")
        oppgaver.add(
            OppgaveV2.ny(
                eksternReferanse = eksternReferanse,
                fagSystem = fagsystem,
                ytelseType = ytelseType,
                oppgaveKode = oppgaveKode,
                opprettet = opprettet,
                søkersId = søkersId,
            )
        )
    }
}
