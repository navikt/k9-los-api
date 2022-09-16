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

    fun lagBehandling(oppgaveversjoner: Set<Oppgave>): Behandling {
        val sisteVersjon = oppgaveversjoner.last()
        return Behandling(
            sakId = null,
            behandlingId = sisteVersjon.eksternId,
            funksjonellTid = OffsetDateTime.of(LocalDateTime.parse(sisteVersjon.eksternVersjon), ZoneOffset.of("Europe/Oslo")),
            tekniskTid = OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.of("Europe/Oslo")),
            mottattDato = LocalDate.now(),
            registrertDato = null,
            vedtaksDato = null,
            relatertBehandlingId = null,
            vedtakId = null, //TODO: callback mot K9?
            saksnummer = sisteVersjon.hentVerdi("saksnummer")?.verdi,
            behandlingType = sisteVersjon.hentVerdi("behandlingTypekode")?.verdi,
            behandlingStatus = sisteVersjon.hentVerdi("behandlingsstatus")?.verdi,
            resultat = sisteVersjon.hentVerdi("resultattype")?.verdi,
            resultatBegrunnelse = null, //TODO: callback mot K9?
            utenlandstilsnitt = null,
            behandlingTypeBeskrivelse = BehandlingType.fraKode(sisteVersjon.hentVerdi("behandlingTypekode")?.verdi).navn,
            behandlingStatusBeskrivelse = BehandlingStatus.fraKode(sisteVersjon.hentVerdi("behandlingsstatus")?.verdi).navn,
            resultatBeskrivelse = BehandlingResultatType.fraKode(sisteVersjon.hentVerdi("resultattype")?.verdi).navn, //resultattype
            resultatBegrunnelseBeskrivelse = null,
            utenlandstilsnittBeskrivelse = null,
            beslutter = null,
            saksbehandler = null,
            behandlingOpprettetAv = null,
            behandlingOpprettetType = null,
            behandlingOpprettetTypeBeskrivelse = null,
            ansvarligEnhetKode = null,
            ansvarligEnhetType = null,
            behandlendeEnhetKode = sisteVersjon.hentVerdi("behandlendeEnhet")?.verdi,
            behandlendeEnhetType = null,
            datoForUttak = null,
            datoForUtbetaling = null, //TODO: callback mot K9?
            totrinnsbehandling = sisteVersjon.hentVerdi("totrinnskontroll")?.verdi.toBoolean(),
            avsender = null,
            versjon = null
        )
    }

}