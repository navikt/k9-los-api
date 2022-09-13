package no.nav.k9.nyoppgavestyring.adapter

import no.nav.k9.nyoppgavestyring.oppgave.OppgaveV3
import no.nav.k9.statistikk.kontrakter.Behandling
import java.time.LocalDate
import java.time.OffsetDateTime

class OppgaveV3TilBehandlingAdapter {

    fun lagBehandling(oppgaveV3: OppgaveV3): Behandling {
        return Behandling(
            sakId = null,
            behandlingId = "",
            funksjonellTid = OffsetDateTime.now(),
            tekniskTid = OffsetDateTime.now(),
            mottattDato = LocalDate.now(),
            registrertDato = null,
            vedtaksDato = null,
            relatertBehandlingId = null,
            vedtakId = null,
            saksnummer = null,
            behandlingType = null,
            behandlingStatus = null,
            resultat = null,
            resultatBegrunnelse = null,
            utenlandstilsnitt = null,
            behandlingTypeBeskrivelse = null,
            behandlingStatusBeskrivelse = null,
            resultatBeskrivelse = null,
            resultatBegrunnelseBeskrivelse = null,
            utenlandstilsnittBeskrivelse = null,
            beslutter = null,
            saksbehandler = null,
            behandlingOpprettetAv = null,
            behandlingOpprettetType = null,
            behandlingOpprettetTypeBeskrivelse = null,
            ansvarligEnhetKode = null,
            ansvarligEnhetType = null,
            behandlendeEnhetKode = null,
            behandlendeEnhetType = null,
            datoForUttak = null,
            datoForUtbetaling = null,
            totrinnsbehandling = null,
            avsender = null,
            versjon = null
        )
    }

}