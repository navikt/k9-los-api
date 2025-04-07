package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj

import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.modell.*
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.IModell
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType

data class K9PunsjModell(
    val eventer: List<PunsjEventDto>
) {

     fun oppgave(): Oppgave {
        return oppgave(eventer[eventer.lastIndex])
    }

    private fun oppgave(sisteEvent: PunsjEventDto): Oppgave {
        val førsteEvent = eventer.first()
        val førsteEventMedJournalførtTidspunktSatt = eventer.firstOrNull { it.journalførtTidspunkt != null }

        var aktiv = sisteEvent.aksjonspunktKoderMedStatusListe.any { aksjonspunkt -> aksjonspunkt.value == "OPPR" }

        if (sisteEvent.tilAksjonspunkter().hentAktive().containsKey("MER_INFORMASJON")) {
            aktiv = false
        }

        return Oppgave(
            behandlingId = null,
            fagsakSaksnummer = "",
            journalpostId = førsteEvent.journalpostId.verdi,
            //må se på siste siden den kan endre seg hvis punsj finner ut at opprinnelig oppgave var på barnets aktør f.eks.
            aktorId = sisteEvent.aktørId?.id ?: "",
            behandlendeEnhet = "",
            behandlingsfrist = førsteEvent.eventTid.toLocalDate().plusDays(21).atStartOfDay(),
            behandlingOpprettet = førsteEvent.eventTid,
            forsteStonadsdag = førsteEvent.eventTid.toLocalDate(),
            behandlingStatus = sisteEvent.utledStatus(),
            behandlingType = (sisteEvent.type ?: førsteEvent.type)?.let { BehandlingType.fraKode(it) } ?: BehandlingType.UKJENT,
            fagsakYtelseType = (sisteEvent.ytelse ?: førsteEvent.ytelse)?.let { FagsakYtelseType.fraKode(it) } ?: FagsakYtelseType.UKJENT,
            eventTid = sisteEvent.eventTid,
            aktiv = aktiv,
            system = "PUNSJ",
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            eksternId = sisteEvent.eksternId,
            oppgaveEgenskap = listOf(),
            aksjonspunkter = sisteEvent.tilAktiveAksjonspunkter(),
            tilBeslutter = false,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            søktGradering = false,
            årskvantum = false,
            avklarMedlemskap = false,
            avklarArbeidsforhold = false,
            kode6 = false,
            skjermet = false,
            utenlands = false,
            vurderopptjeningsvilkåret = false,
            ansvarligSaksbehandlerForTotrinn = null,
            ansvarligSaksbehandlerIdent = null,
            kombinert = false,
            pleietrengendeAktørId = sisteEvent.pleietrengendeAktørId,
            journalførtTidspunkt = førsteEventMedJournalførtTidspunktSatt?.journalførtTidspunkt
        )
    }

}

fun PunsjEventDto.utledStatus() : BehandlingStatus {
    val aksjonspunkter = tilAksjonspunkter()
    if (aksjonspunkter.hentAktive().containsKey("MER_INFORMASJON")) {
        return BehandlingStatus.SATT_PÅ_VENT
    } else if (sendtInn != null && sendtInn) {
        return BehandlingStatus.SENDT_INN
    } else if (aksjonspunkter.erIngenAktive() && (sendtInn == null || !sendtInn)) {
        return BehandlingStatus.LUKKET
    }
    return BehandlingStatus.OPPRETTET
}
