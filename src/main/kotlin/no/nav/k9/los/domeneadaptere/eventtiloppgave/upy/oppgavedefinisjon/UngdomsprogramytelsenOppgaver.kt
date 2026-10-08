package no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.oppgavedefinisjon

import no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.kodeverk.UpyOppgavetypenavn
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavefeltDto
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavetypeDto
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavetyperDto

object UngdomsprogramytelsenOppgaver {
    /**
     * @param frontendUrl settes inn direkte i URL-malen. Plassholdere i `{}` tolkes som oppgavefelt
     * av [no.nav.k9.los.oppgaveuthenting.Oppgave.getOppgaveBehandlingsurl], så base-URL-en kan ikke
     * ligge igjen som plassholder.
     */
    fun lagOppgaveDefinisjon(frontendUrl: String): OppgavetyperDto {
        return OppgavetyperDto(
            område = Områder.UNGDOMSPROGRAMYTELSEN,
            oppgavetyper = setOf(
                lagUngdomsprogramytelsenOrdinær(UpyOppgavetypenavn.UNGDOMSPROGRAMYTELSENORDINÆR.kode, frontendUrl),
                //TODO: klage, feilutbetaling
            )
        )
    }

    private fun lagUngdomsprogramytelsenOrdinær(id: String, frontendUrl: String): OppgavetypeDto {
        return OppgavetypeDto(
            id = id,
            oppgavebehandlingsUrlTemplate = "$frontendUrl/fagsak/{${UngdomsprogramytelsenFeltIder.Sak.SAKSNUMMER}}/",
            oppgavefelter = setOf(
                // Behandling
                OppgavefeltDto(UngdomsprogramytelsenFeltIder.Behandling.UUID, visPåOppgave = true, påkrevd = true),
                OppgavefeltDto(UngdomsprogramytelsenFeltIder.Behandling.TYPEKODE, visPåOppgave = true, påkrevd = true),
                OppgavefeltDto(UngdomsprogramytelsenFeltIder.Behandling.STATUS, visPåOppgave = true, påkrevd = true),
                OppgavefeltDto(UngdomsprogramytelsenFeltIder.Behandling.STEG, visPåOppgave = true, påkrevd = false),
                OppgavefeltDto(UngdomsprogramytelsenFeltIder.Behandling.ARSAK, visPåOppgave = true, påkrevd = false),
                OppgavefeltDto(UngdomsprogramytelsenFeltIder.Behandling.RESULTATTYPE, visPåOppgave = true, påkrevd = true),
                // Soknad
                OppgavefeltDto(UngdomsprogramytelsenFeltIder.Soknad.NYE_KRAV, visPåOppgave = true, påkrevd = false),
                // Sak
                OppgavefeltDto(UngdomsprogramytelsenFeltIder.Sak.AKTOR_ID, visPåOppgave = true, påkrevd = true),
                OppgavefeltDto(UngdomsprogramytelsenFeltIder.Sak.FAGSYSTEM, visPåOppgave = false, påkrevd = true),
                OppgavefeltDto(UngdomsprogramytelsenFeltIder.Sak.SAKSNUMMER, visPåOppgave = true, påkrevd = true),
                OppgavefeltDto(UngdomsprogramytelsenFeltIder.Sak.MOTTATT_DATO, visPåOppgave = true, påkrevd = false),
                OppgavefeltDto(UngdomsprogramytelsenFeltIder.Sak.TID_SIDEN_MOTTATT_DATO, visPåOppgave = true, påkrevd = false),
                OppgavefeltDto(UngdomsprogramytelsenFeltIder.Sak.REGISTRERT_DATO, visPåOppgave = true, påkrevd = false),
                // Vedtak
                OppgavefeltDto(UngdomsprogramytelsenFeltIder.Vedtak.DATO, visPåOppgave = true, påkrevd = false),
                OppgavefeltDto(UngdomsprogramytelsenFeltIder.Vedtak.YTELSESTYPE, visPåOppgave = true, påkrevd = true),
                OppgavefeltDto(UngdomsprogramytelsenFeltIder.Vedtak.BEHANDLENDE_ENHET, visPåOppgave = true, påkrevd = false),
                OppgavefeltDto(UngdomsprogramytelsenFeltIder.Vedtak.TOTRINNSKONTROLL, visPåOppgave = true, påkrevd = true),
                // Aksjonspunkt
                OppgavefeltDto(UngdomsprogramytelsenFeltIder.Aksjonspunkt.ALLE, visPåOppgave = true, påkrevd = false),
                OppgavefeltDto(UngdomsprogramytelsenFeltIder.Aksjonspunkt.AKTIVE, visPåOppgave = true, påkrevd = false),
                OppgavefeltDto(UngdomsprogramytelsenFeltIder.Aksjonspunkt.LOSBART, visPåOppgave = true, påkrevd = false),
                OppgavefeltDto(UngdomsprogramytelsenFeltIder.Aksjonspunkt.UTFORT, visPåOppgave = true, påkrevd = false),
                OppgavefeltDto(UngdomsprogramytelsenFeltIder.Aksjonspunkt.AVBRUTT, visPåOppgave = true, påkrevd = false),
                OppgavefeltDto(UngdomsprogramytelsenFeltIder.Aksjonspunkt.FREMTIDIG, visPåOppgave = true, påkrevd = false),
                // Saksbehandling
                OppgavefeltDto(UngdomsprogramytelsenFeltIder.Saksbehandling.ANSVARLIG_SAKSBEHANDLER, visPåOppgave = true, påkrevd = false),
                OppgavefeltDto(UngdomsprogramytelsenFeltIder.Saksbehandling.ANSVARLIG_BESLUTTER, visPåOppgave = true, påkrevd = false),
                OppgavefeltDto(UngdomsprogramytelsenFeltIder.Saksbehandling.LIGGER_HOS_BESLUTTER, visPåOppgave = true, påkrevd = false),
                OppgavefeltDto(UngdomsprogramytelsenFeltIder.Saksbehandling.TID_FORSTE_GANG_HOS_BESLUTTER, visPåOppgave = true, påkrevd = false),
                // Ventetid
                OppgavefeltDto(UngdomsprogramytelsenFeltIder.Ventetid.AKTIV_ARSAK, visPåOppgave = true, påkrevd = false),
                OppgavefeltDto(UngdomsprogramytelsenFeltIder.Ventetid.AKTIV_FRIST, visPåOppgave = true, påkrevd = false),
            )
        )
    }
}