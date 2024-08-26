package no.nav.k9.los.domene.lager.oppgave

import no.nav.k9.los.domene.modell.Aksjonspunkter
import no.nav.k9.los.domene.modell.BehandlingStatus
import no.nav.k9.los.domene.modell.BehandlingType
import no.nav.k9.los.domene.modell.FagsakYtelseType
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

//TODO burde splittes i spesifikke oppgaver, SakOppgave, PunsjOppgave, TilbakeOppgave f.eks.
data class Oppgave(
    @Deprecated("Bruk eksternId") val behandlingId: Long? = null,
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
    val relatertPartAktørId: String? = null,
    val feilutbetaltBeløp: Long? = null,
    val nyeKrav: Boolean? = null,
    var fraEndringsdialog: Boolean? = false,
    val journalførtTidspunkt: LocalDateTime? = null
) {
    fun avluttet(): Boolean {
        return behandlingStatus == BehandlingStatus.AVSLUTTET
    }

    data class FagsakPeriode(
        val fom: LocalDate, val tom: LocalDate
    )

    //TODO Dette burde endres. FagsaksNummer er ikke nullable, men kan pr dags dato være en tom streng. Burde derfor endres til String?
    fun harFagSaksNummer(): Boolean {
        return fagsakSaksnummer.isNotBlank()
    }
}

data class OppgaveMedId(
    val id: UUID, val oppgave: Oppgave
)

