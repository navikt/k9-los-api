package no.nav.k9.nyoppgavestyring.adaptere.statistikkadapter

import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.kodeverk.behandling.BehandlingStatus
import no.nav.k9.kodeverk.behandling.BehandlingType
import no.nav.k9.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.statistikk.kontrakter.Behandling
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

class OppgaveV3TilBehandlingAdapter {

    fun lagBehandling(oppgaveversjoner: Set<OppgaveV3>): Behandling {
        val sisteVersjon = oppgaveversjoner.first()
        return Behandling(
            sakId = null,
            behandlingId = sisteVersjon.eksternId,
            funksjonellTid = OffsetDateTime.of(LocalDateTime.parse(sisteVersjon.eksternVersjon), ZoneOffset.UTC),
            tekniskTid = OffsetDateTime.now(),
            mottattDato = LocalDate.now(),
            registrertDato = null,
            vedtaksDato = null,
            relatertBehandlingId = null,
            vedtakId = null, //TODO: callback mot K9?
            saksnummer = sisteVersjon.hentVerdi("saksnummer")?.verdi,
            behandlingType = sisteVersjon.hentVerdi("behandlingTypekode")?.verdi,
            behandlingStatus = sisteVersjon.hentVerdi("behandnlingsstatus")?.verdi,
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