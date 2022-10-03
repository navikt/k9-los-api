package no.nav.k9.nyoppgavestyring.domeneadaptere.statistikk

import no.nav.k9.kodeverk.behandling.BehandlingStatus
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktKodeDefinisjon
import no.nav.k9.nyoppgavestyring.visningoguttrekk.Oppgave
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

class OppgaveTilBehandlingMapper {

    companion object {
        val zoneId = ZoneId.of("Europe/Oslo")
    }

    fun lagBehandling(oppgave: Oppgave): Behandling {
        return Behandling(
            sakId = oppgave.hentVerdi("saksnummer"),
            behandlingId = oppgave.eksternId,
            funksjonellTid = LocalDateTime.parse(oppgave.eksternVersjon).atZone(zoneId).toOffsetDateTime(),
            tekniskTid = OffsetDateTime.now(zoneId),
            mottattDato = LocalDateTime.parse(oppgave.hentVerdi("mottattDato")).toLocalDate(),
            registrertDato = LocalDateTime.parse(oppgave.hentVerdi("registrertDato")).toLocalDate(),
            vedtaksDato = oppgave.hentVerdi("vedtaksDato")
                ?.let { LocalDate.parse(it) }, // TODO vedtakstopic, YtelseV1.vedtattTidspunkt
            relatertBehandlingId = null,
            vedtakId = oppgave.hentVerdi("vedtakId"), //TODO: callback mot K9? evt vedtakstopic, YtelseV1.vedtakReferanse
            saksnummer = oppgave.hentVerdi("saksnummer"),
            behandlingType = oppgave.hentVerdi("behandlingTypekode")
                ?.let { no.nav.k9.domene.modell.BehandlingType.fraKode(it).navn }, //TODO: Bruke navn direkte her?
            behandlingStatus = BehandlingStatus.fraKode(oppgave.hentVerdi("behandlingsstatus")).navn,
            resultat = oppgave.hentVerdi("resultattype"),
            resultatBegrunnelse = null, //TODO: callback mot K9?
            utenlandstilsnitt = utledUtenlandstilsnitt(oppgave),
            //behandlingTypeBeskrivelse = BehandlingType.fraKode(sisteVersjon.hentVerdi("behandlingTypekode")).navn,
            //behandlingStatusBeskrivelse = BehandlingStatus.fraKode(sisteVersjon.hentVerdi("behandlingsstatus")).navn,
            //resultatBeskrivelse = BehandlingResultatType.fraKode(sisteVersjon.hentVerdi("resultattype")).navn, //resultattype
            resultatBegrunnelseBeskrivelse = null,
            utenlandstilsnittBeskrivelse = null,
            beslutter = oppgave.hentVerdi("ansvarligBeslutterForTotrinn") ?: "", //TODO riktig?
            saksbehandler = utledAnsvarligSaksbehandler(oppgave),
            behandlingOpprettetAv = "system",
            behandlingOpprettetType = null,
            behandlingOpprettetTypeBeskrivelse = null,
            ansvarligEnhetKode = oppgave.hentVerdi("behandlendeEnhet") ?: "SRV",
            ansvarligEnhetType = "NORG",
            behandlendeEnhetKode = oppgave.hentVerdi("behandlendeEnhet") ?: "SRV",
            behandlendeEnhetType = "NORG",
            datoForUttak = null, // TODO: mappes fra YtelseV1.anvist.firstOrNull()?.periode?.fom, men trengs ikke?
            datoForUtbetaling = null, //TODO: trengs ikke?
            totrinnsbehandling = oppgave.hentVerdi("totrinnskontroll").toBoolean(),
            avsender = "K9sak",
            versjon = 1, //TODO: Ikke i bruk?
        )
    }

    private fun utledAnsvarligSaksbehandler(oppgave: Oppgave): String? {
        val ansvarligSaksbehandlerForTotrinn = oppgave.hentVerdi("ansvarligSaksbehandlerForTotrinn")
        val ansvarligSaksbehandlerIdent = oppgave.hentVerdi("ansvarligSaksbehandlerIdent")

        if (null != ansvarligSaksbehandlerForTotrinn) {
            return ansvarligSaksbehandlerForTotrinn
        } else if (null != ansvarligSaksbehandlerIdent) {
            return ansvarligSaksbehandlerIdent
        }
        return null
    }

    private fun utledUtenlandstilsnitt(oppgave: Oppgave): String {
        return oppgave.hentListeverdi("aktivtAksjonspunkt").any { aksjonspunktKode ->
            aksjonspunktKode.equals(AksjonspunktKodeDefinisjon.AUTOMATISK_MARKERING_AV_UTENLANDSSAK_KODE)
                    || aksjonspunktKode.equals(AksjonspunktKodeDefinisjon.MANUELL_MARKERING_AV_UTLAND_SAKSTYPE_KODE)
        }.toString()
    }


}