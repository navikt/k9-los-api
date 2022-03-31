package no.nav.k9.domene.lager.oppgave.v2

import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class OppgaveV2(
    var id: Long?,
    val eksternReferanse: String,
    val oppgaveKode: String,
    val erBeslutter: Boolean,
    var oppgaveStatus: OppgaveStatus,
    val opprettet: LocalDateTime = LocalDateTime.now(),
    var sistEndret: LocalDateTime = opprettet,
    val frist: LocalDateTime?,
) {
    var ferdigstilt: Ferdigstilt? = null
        set(value) {
            if (ferdigstilt == null) field = value
        }

    fun avbrytOppgaveUtenFerdigstillelse() {
        log.info("Avbryter oppgave $oppgaveKode for $eksternReferanse")
        ferdigstilt = null
        sistEndret = LocalDateTime.now()
        oppgaveStatus = OppgaveStatus.AVBRUTT
    }

    fun ferdigstill(ferdigstillelse: Ferdigstillelse) {
        oppgaveStatus = OppgaveStatus.FERDIGSTILT

        if (ferdigstilt == null) {
            log.info("Ferdigstiller oppgave $oppgaveKode for $eksternReferanse")
            ferdigstilt = Ferdigstilt(
                tidspunkt = ferdigstillelse.tidspunkt,
                ansvarligSaksbehandlerIdent = ferdigstillelse.ansvarligSaksbehandlerIdent,
                behandlendeEnhet = ferdigstillelse.behandlendeEnhet
            )
            sistEndret = ferdigstillelse.tidspunkt
        }
    }

    fun erAktiv(): Boolean {
        return oppgaveStatus.erAktiv()
    }

    fun erFerdigstilt(): Boolean {
        return oppgaveStatus.erFerdigstilt()
    }

    companion object {
        private val log = LoggerFactory.getLogger(OppgaveV2::class.java)

        fun ny(
            eksternReferanse: String,
            oppgaveKode: String,
            opprettet: LocalDateTime,
            beslutter: Boolean = false,
            frist: LocalDateTime? = null,
        ) =
            OppgaveV2(
                id = null,
                oppgaveStatus = OppgaveStatus.OPPRETTET,
                eksternReferanse = eksternReferanse,
                oppgaveKode = oppgaveKode,
                opprettet = opprettet,
                erBeslutter = beslutter,
                frist = frist
            )
    }

    data class Ferdigstilt(
        val tidspunkt: LocalDateTime,
        val ansvarligSaksbehandlerIdent: String? = null,
        val behandlendeEnhet: String? = null,
    )
}