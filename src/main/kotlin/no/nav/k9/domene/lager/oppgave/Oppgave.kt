package no.nav.k9.domene.lager.oppgave

import no.nav.k9.domene.modell.AksjonspunktStatus
import no.nav.k9.domene.modell.Aksjonspunkter
import no.nav.k9.domene.modell.BehandlingStatus
import no.nav.k9.domene.modell.BehandlingType
import no.nav.k9.domene.modell.FagsakYtelseType
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class Oppgave(
    val behandlingId: Long?,
    val fagsakSaksnummer: String,
    val journalpostId: String?,
    val aktorId: String,
    val behandlendeEnhet: String,
    val behandlingsfrist: LocalDateTime,
    val behandlingOpprettet: LocalDateTime,
    val forsteStonadsdag: LocalDate,
    var behandlingStatus: BehandlingStatus,
    val behandlingType: BehandlingType,
    val fagsakYtelseType: FagsakYtelseType,
    val eventTid: LocalDateTime = LocalDateTime.now(),
    val aktiv: Boolean,
    val system: String,
    val oppgaveAvsluttet: LocalDateTime?,
    val utfortFraAdmin: Boolean,
    val eksternId: UUID,
    val oppgaveEgenskap: List<OppgaveEgenskap>,
    val aksjonspunkter: Aksjonspunkter,
    val tilBeslutter: Boolean,
    val utbetalingTilBruker: Boolean,
    val selvstendigFrilans: Boolean,
    //todo skal ikke brukes
    val kombinert: Boolean,
    val søktGradering: Boolean,
    val årskvantum: Boolean,
    val avklarMedlemskap: Boolean,
    val avklarArbeidsforhold: Boolean,
    var kode6: Boolean = false,
    var skjermet: Boolean = false,
    val utenlands: Boolean,
    val vurderopptjeningsvilkåret: Boolean = false,
    val ansvarligSaksbehandlerForTotrinn: String? = null,
    val ansvarligSaksbehandlerIdent: String? = null,
    val ansvarligBeslutterForTotrinn: String? = null,
    val fagsakPeriode: FagsakPeriode? = null,
    val pleietrengendeAktørId: String? = null,
    val relatertPartAktørId: String? = null

) {
    fun avluttet(): Boolean {
        return behandlingStatus == BehandlingStatus.AVSLUTTET
    }

    data class FagsakPeriode(
        val fom: LocalDate, val tom: LocalDate
    )


}

data class OppgaveMedId(
    val id: UUID, val oppgave: Oppgave
)

data class AksjonspunktTilstand(
    val aksjonspunktKode: String,
    val status: AksjonspunktStatus,
    val venteårsak: String? = null,
    val frist: LocalDateTime? = null
)