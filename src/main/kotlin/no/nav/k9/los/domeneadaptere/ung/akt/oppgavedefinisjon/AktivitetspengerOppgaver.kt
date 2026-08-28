package no.nav.k9.los.domeneadaptere.ung.akt.oppgavedefinisjon

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavefeltDto
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavetypeDto
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavetyperDto

object AktivitetspengerOppgaver {
    fun lagOppgaveDefinisjon(): OppgavetyperDto {
        return OppgavetyperDto(
            område = Områder.AKTIVITETSPENGER.eksternId,
            definisjonskilde = "ung-sak-til-los",
            oppgavetyper = setOf(
                lagAktivitetspengerOrdinær("AktivitetspengerOrdinærDel1"),
                lagAktivitetspengerOrdinær("AktivitetspengerOrdinærDel2")
                //TODO: klage, feilutbetaling
            )
        )
    }

    private fun lagAktivitetspengerOrdinær(id: String): OppgavetypeDto {
        return OppgavetypeDto(
            id = id,
            oppgavebehandlingsUrlTemplate = "{baseUrl}/fagsak/{K9.saksnummer}/",
            oppgavefelter = setOf(
                // Behandling
                OppgavefeltDto(AktivitetspengerFeltIder.Behandling.UUID, visPåOppgave = true, påkrevd = true),
                OppgavefeltDto(AktivitetspengerFeltIder.Behandling.TYPEKODE, visPåOppgave = true, påkrevd = true),
                OppgavefeltDto(AktivitetspengerFeltIder.Behandling.STATUS, visPåOppgave = true, påkrevd = true),
                OppgavefeltDto(AktivitetspengerFeltIder.Behandling.STEG, visPåOppgave = true, påkrevd = false),
                OppgavefeltDto(AktivitetspengerFeltIder.Behandling.ARSAK, visPåOppgave = true, påkrevd = false),
                // Soknad
                OppgavefeltDto(AktivitetspengerFeltIder.Soknad.NYE_KRAV, visPåOppgave = true, påkrevd = false),
                OppgavefeltDto(AktivitetspengerFeltIder.Soknad.ARSAK, visPåOppgave = true, påkrevd = false),
                OppgavefeltDto(AktivitetspengerFeltIder.Soknad.FRA_ENDRINGSDIALOG, visPåOppgave = false, påkrevd = false),
                // Sak
                OppgavefeltDto(AktivitetspengerFeltIder.Sak.AKTOR_ID, visPåOppgave = true, påkrevd = true),
                OppgavefeltDto(AktivitetspengerFeltIder.Sak.FAGSYSTEM, visPåOppgave = false, påkrevd = true),
                OppgavefeltDto(AktivitetspengerFeltIder.Sak.SAKSNUMMER, visPåOppgave = true, påkrevd = true),
                OppgavefeltDto(AktivitetspengerFeltIder.Sak.MOTTATT_DATO, visPåOppgave = true, påkrevd = false),
                OppgavefeltDto(AktivitetspengerFeltIder.Sak.TID_SIDEN_MOTTATT_DATO, visPåOppgave = true, påkrevd = false),
                // Vedtak
                OppgavefeltDto(AktivitetspengerFeltIder.Vedtak.DATO, visPåOppgave = true, påkrevd = false),
                OppgavefeltDto(AktivitetspengerFeltIder.Vedtak.RESULTATTYPE, visPåOppgave = true, påkrevd = true),
                OppgavefeltDto(AktivitetspengerFeltIder.Vedtak.YTELSESTYPE, visPåOppgave = true, påkrevd = true),
                OppgavefeltDto(AktivitetspengerFeltIder.Vedtak.BEHANDLENDE_ENHET, visPåOppgave = true, påkrevd = false),
                OppgavefeltDto(AktivitetspengerFeltIder.Vedtak.TOTRINNSKONTROLL, visPåOppgave = true, påkrevd = true),
                // Aksjonspunkt
                OppgavefeltDto(AktivitetspengerFeltIder.Aksjonspunkt.ALLE, visPåOppgave = true, påkrevd = false),
                OppgavefeltDto(AktivitetspengerFeltIder.Aksjonspunkt.AKTIVE, visPåOppgave = true, påkrevd = false),
                OppgavefeltDto(AktivitetspengerFeltIder.Aksjonspunkt.LOSBART, visPåOppgave = true, påkrevd = false),
                OppgavefeltDto(AktivitetspengerFeltIder.Aksjonspunkt.UTFORT, visPåOppgave = true, påkrevd = false),
                OppgavefeltDto(AktivitetspengerFeltIder.Aksjonspunkt.AVBRUTT, visPåOppgave = true, påkrevd = false),
                OppgavefeltDto(AktivitetspengerFeltIder.Aksjonspunkt.FREMTIDIG, visPåOppgave = true, påkrevd = false),
                // Beslutter
                OppgavefeltDto(AktivitetspengerFeltIder.Beslutter.ANSVARLIG, visPåOppgave = true, påkrevd = false),
                OppgavefeltDto(AktivitetspengerFeltIder.Beslutter.LIGGER_HOS, visPåOppgave = true, påkrevd = false),
                OppgavefeltDto(AktivitetspengerFeltIder.Beslutter.TID_FORSTE_GANG_HOS, visPåOppgave = true, påkrevd = false),
                // Ventetid
                OppgavefeltDto(AktivitetspengerFeltIder.Ventetid.AKTIV_ARSAK, visPåOppgave = true, påkrevd = false),
                OppgavefeltDto(AktivitetspengerFeltIder.Ventetid.AKTIV_FRIST, visPåOppgave = true, påkrevd = false),
            )
        )
    }
}