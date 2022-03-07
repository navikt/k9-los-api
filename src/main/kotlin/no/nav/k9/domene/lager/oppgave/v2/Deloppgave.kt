package no.nav.k9.domene.lager.oppgave.v2

import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

data class Deloppgave(
    val id: UUID,
    val eksternReferanse: String,
    val oppgaveKode: String,
    val erBeslutter: Boolean,
    var oppgaveStatus: OppgaveStatus,
    val opprettet: LocalDateTime = LocalDateTime.now(),
    var sistEndret: LocalDateTime = opprettet,
    val frist: LocalDateTime?,
) {
    var ferdistilt: Ferdigstilt? = null

    fun avbrytOppgaveUtenFerdigstillelse() {
        sistEndret = LocalDateTime.now()
        oppgaveStatus = OppgaveStatus.AVBRUTT
    }

    fun ferdigstill(ferdigstillelse: Ferdigstillelse) {
        oppgaveStatus = OppgaveStatus.FERDIGSTILT
        sistEndret = ferdigstillelse.tidspunkt

        if (ferdistilt == null) {
            log.info("Ferdigstiller oppgave $oppgaveKode for $eksternReferanse")
            ferdistilt = Ferdigstilt(
                tidspunkt = ferdigstillelse.tidspunkt,
                ansvarligSaksbehandlerIdent = ferdigstillelse.ansvarligSaksbehandlerIdent,
                behandlendeEnhet = ferdigstillelse.behandlendeEnhet
            )
        }
    }

    fun erAktiv(): Boolean {
        return oppgaveStatus.erAktiv()
    }

    companion object {
        private val log = LoggerFactory.getLogger(Deloppgave::class.java)

        fun ny(
            eksternReferanse: String,
            oppgaveKode: String,
            opprettet: LocalDateTime,
            beslutter: Boolean = false,
            frist: LocalDateTime? = null,
        ) =
            Deloppgave(
                id = UUID.randomUUID(),
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