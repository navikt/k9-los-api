package no.nav.k9.nyoppgavestyring.adaptere.statistikkadapter

import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.kodeverk.behandling.BehandlingStatus
import no.nav.k9.kodeverk.behandling.BehandlingType
import no.nav.k9.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.statistikk.kontrakter.Behandling
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

class OppgaveTilBehandlingAdapter {

    companion object {
        val zoneOffset = ZoneOffset.of("Europe/Oslo")
    }

    fun lagBehandling(oppgaveversjoner: Set<Oppgave>): Behandling {
        val sisteVersjon = oppgaveversjoner.last()
        return Behandling(
            sakId = null,
            behandlingId = sisteVersjon.eksternId,
            funksjonellTid = OffsetDateTime.of(LocalDateTime.parse(sisteVersjon.eksternVersjon), zoneOffset),
            tekniskTid = OffsetDateTime.now(zoneOffset),
            mottattDato = LocalDate.now(),
            registrertDato = null,
            vedtaksDato = null,
            relatertBehandlingId = null,
            vedtakId = null, //TODO: callback mot K9?
            saksnummer = sisteVersjon.hentVerdi("saksnummer"),
            behandlingType = sisteVersjon.hentVerdi("behandlingTypekode"),
            behandlingStatus = sisteVersjon.hentVerdi("behandlingsstatus"),
            resultat = sisteVersjon.hentVerdi("resultattype"),
            resultatBegrunnelse = null, //TODO: callback mot K9?
            utenlandstilsnitt = null,
            behandlingTypeBeskrivelse = BehandlingType.fraKode(sisteVersjon.hentVerdi("behandlingTypekode")).navn,
            behandlingStatusBeskrivelse = BehandlingStatus.fraKode(sisteVersjon.hentVerdi("behandlingsstatus")).navn,
            resultatBeskrivelse = BehandlingResultatType.fraKode(sisteVersjon.hentVerdi("resultattype")).navn, //resultattype
            resultatBegrunnelseBeskrivelse = null,
            utenlandstilsnittBeskrivelse = null,
            beslutter = null,
            saksbehandler = null,
            behandlingOpprettetAv = null,
            behandlingOpprettetType = null,
            behandlingOpprettetTypeBeskrivelse = null,
            ansvarligEnhetKode = null,
            ansvarligEnhetType = null,
            behandlendeEnhetKode = sisteVersjon.hentVerdi("behandlendeEnhet"),
            behandlendeEnhetType = null,
            datoForUttak = null,
            datoForUtbetaling = null, //TODO: callback mot K9?
            totrinnsbehandling = sisteVersjon.hentVerdi("totrinnskontroll").toBoolean(),
            avsender = null,
            versjon = null
        )
    }

}