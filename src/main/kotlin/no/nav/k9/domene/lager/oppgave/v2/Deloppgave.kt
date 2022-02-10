package no.nav.k9.domene.lager.oppgave.v2

import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.modell.Fagsystem
import java.time.LocalDateTime
import java.util.*

data class OppgaveV2(
    val id: UUID,
    val eksternReferanse: String,
    var oppgaveStatus: OppgaveStatus,
    val opprettet: LocalDateTime = LocalDateTime.now(),
    var sistEndret: LocalDateTime = opprettet,
    val oppgaveKode: String?,
    val frist: LocalDateTime? = null,
) {
    var totrinn: Totrinnskontroll? = null
        private set

    var ferdistilt: Ferdigstillelse? = null

    fun avbrytOppgaveUtenFerdigstillelse() {
        sistEndret = LocalDateTime.now()
        oppgaveStatus = OppgaveStatus.AVBRUTT
    }

    fun ferdigstill(
        tidspunkt: LocalDateTime,
        ansvarligSaksbehandler: String? = null,
        behandlendeEnhet: String? = null
    ) {
        oppgaveStatus = OppgaveStatus.FERDIGSTILT
        sistEndret = tidspunkt

        if (ferdistilt == null) {
            ferdistilt = Ferdigstillelse(tidspunkt, ansvarligSaksbehandler, behandlendeEnhet)
        }
    }

    fun erAktiv(): Boolean {
        return oppgaveStatus.erAktiv()
    }

    companion object {
        fun ny(
            fagSystem: Fagsystem,
            ytelseType: FagsakYtelseType,
            eksternReferanse: String,
            oppgaveKode: String? = null,
            opprettet: LocalDateTime,
            søkersId: Ident?,
        ) =
            OppgaveV2(
                id = UUID.randomUUID(),
                eksternReferanse = eksternReferanse,
                oppgaveKode = oppgaveKode,
                oppgaveStatus = OppgaveStatus.OPPRETTET,
            )
    }
}

data class Ident(
        val id: String,
        val idType: IdType,
) {
    enum class IdType {
        ORGNR,
        AKTØRID,
    }
}

class Ferdigstillelse(
    val tidspunkt: LocalDateTime,
    val ansvarligSaksbehandlerIdent: String? = null,
    val behandlendeEnhet: String? = null,
)

enum class OppgaveStatus(val kode: String) {
    OPPRETTET("OPPRETTET"),
    UNDER_BEHANDLING("UNDER_BEHANDLING"),
    AVBRUTT("AVBRUTT"),
    FERDIGSTILT("FERDIGSTILT"),
    FEILREGISTRERT("FEILREGISTRERT");

    companion object {
        val aktivOppgaveKoder = EnumSet.of(OppgaveStatus.OPPRETTET, OppgaveStatus.UNDER_BEHANDLING)
    }

    fun erAktiv(): Boolean {
        return aktivOppgaveKoder.contains(this)
    }
}

class Totrinnskontroll (
) {
    var utført: LocalDateTime? = null
    var utførtAvIdent: String? = null

    fun erPåkrevd(): Boolean {
        return true
    }

    fun venterPåBeslutter(): Boolean {
        if (erPåkrevd()) {
            return utført != null
        } else {
            return false
        }
    }

    fun utfør(ident: String) {
        this.utførtAvIdent = ident
        utført = LocalDateTime.now()
    }
}

enum class OppgaveKommando {
    OPPRETT,
    SETT_PÅ_VENT,
    FERDIGSTILL
}
