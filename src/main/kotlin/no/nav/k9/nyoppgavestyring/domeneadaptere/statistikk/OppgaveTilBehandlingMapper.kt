package no.nav.k9.nyoppgavestyring.domeneadaptere.statistikk

import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.kodeverk.behandling.BehandlingStatus
import no.nav.k9.kodeverk.behandling.BehandlingType
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktKodeDefinisjon
import no.nav.k9.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.statistikk.kontrakter.Behandling
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

class OppgaveTilBehandlingMapper {

    companion object {
        val zoneOffset = ZoneOffset.of("Europe/Oslo")
    }

    fun lagBehandling(oppgaveversjoner: Set<Oppgave>): Behandling {
        val sisteVersjon = oppgaveversjoner.last()
        return Behandling(
            sakId = sisteVersjon.hentVerdi("saksnummer"),
            behandlingId = sisteVersjon.eksternId,
            funksjonellTid = OffsetDateTime.of(LocalDateTime.parse(sisteVersjon.eksternVersjon), zoneOffset),
            tekniskTid = OffsetDateTime.now(zoneOffset),
            mottattDato = oppgaveversjoner.first().endretTidspunkt.toLocalDate(),
            registrertDato = oppgaveversjoner.first().endretTidspunkt.toLocalDate(),
            vedtaksDato = null, // TODO vedtakstopic, YtelseV1.vedtattTidspunkt
            relatertBehandlingId = null,
            vedtakId = null, //TODO: callback mot K9? evt vedtakstopic, YtelseV1.vedtakReferanse
            saksnummer = sisteVersjon.hentVerdi("saksnummer"),
            behandlingType = sisteVersjon.hentVerdi("behandlingTypekode"), //TODO: Bruke navn direkte her?
            behandlingStatus = sisteVersjon.hentVerdi("behandlingsstatus"),
            resultat = sisteVersjon.hentVerdi("resultattype"),
            resultatBegrunnelse = null, //TODO: callback mot K9?
            utenlandstilsnitt = utledUtenlandstilsnitt(sisteVersjon),
            behandlingTypeBeskrivelse = BehandlingType.fraKode(sisteVersjon.hentVerdi("behandlingTypekode")).navn,
            behandlingStatusBeskrivelse = BehandlingStatus.fraKode(sisteVersjon.hentVerdi("behandlingsstatus")).navn,
            resultatBeskrivelse = BehandlingResultatType.fraKode(sisteVersjon.hentVerdi("resultattype")).navn, //resultattype
            resultatBegrunnelseBeskrivelse = null,
            utenlandstilsnittBeskrivelse = null,
            beslutter = sisteVersjon.hentVerdi("ansvarligBeslutterForTotrinn"), //TODO riktig?
            saksbehandler = utledAnsvarligSaksbehandler(oppgaveversjoner),
            behandlingOpprettetAv = "system",
            behandlingOpprettetType = null,
            behandlingOpprettetTypeBeskrivelse = null,
            ansvarligEnhetKode = sisteVersjon.hentVerdi("behandlendeEnhet"),
            ansvarligEnhetType = "NORG",
            behandlendeEnhetKode = sisteVersjon.hentVerdi("behandlendeEnhet"),
            behandlendeEnhetType = "NORG",
            datoForUttak = null, // TODO: mappes fra YtelseV1.anvist.firstOrNull()?.periode?.fom, men trengs ikke?
            datoForUtbetaling = null, //TODO: trengs ikke?
            totrinnsbehandling = sisteVersjon.hentVerdi("totrinnskontroll").toBoolean(),
            avsender = "k9sak",
            versjon = 1, //TODO: Ikke i bruk?
        )
    }

    private fun utledAnsvarligSaksbehandler(oppgaveversjoner: Set<Oppgave>): String? {
        for (versjon in oppgaveversjoner.reversed()) {
            val ansvarligSaksbehandlerForTotrinn = versjon.hentVerdi("ansvarligSaksbehandlerForTotrinn")
            val ansvarligSaksbehandlerIdent = versjon.hentVerdi("ansvarligSaksbehandlerIdent")
            if (null != ansvarligSaksbehandlerForTotrinn) {
                return ansvarligSaksbehandlerForTotrinn
            } else if (null != ansvarligSaksbehandlerIdent) {
                return ansvarligSaksbehandlerIdent
            }
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