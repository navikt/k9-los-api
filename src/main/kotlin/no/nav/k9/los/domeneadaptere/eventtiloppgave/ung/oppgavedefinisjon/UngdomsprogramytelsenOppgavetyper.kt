package no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.oppgavedefinisjon

import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.kodeverk.UngOppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.oppgavedefinisjon.UngdomsprogramytelsenFelter.Aksjonspunkt
import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.oppgavedefinisjon.UngdomsprogramytelsenFelter.Behandling
import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.oppgavedefinisjon.UngdomsprogramytelsenFelter.Sak
import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.oppgavedefinisjon.UngdomsprogramytelsenFelter.Saksbehandling
import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.oppgavedefinisjon.UngdomsprogramytelsenFelter.Soknad
import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.oppgavedefinisjon.UngdomsprogramytelsenFelter.Vedtak
import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.oppgavedefinisjon.UngdomsprogramytelsenFelter.Ventetid
import no.nav.k9.los.oppgavedefinisjon.typesikker.Oppgavefeltsett
import no.nav.k9.los.oppgavedefinisjon.typesikker.Oppgavetypedefinisjon
import no.nav.k9.los.oppgavedefinisjon.typesikker.Oppgavetypekatalog

object UngdomsprogramytelsenOppgavetyper : Oppgavetypekatalog(UngdomsprogramytelsenFelter) {

    private val fellesfelter = Oppgavefeltsett {
        påkrevd(Behandling.UUID)
        påkrevd(Behandling.TYPEKODE)
        påkrevd(Behandling.STATUS)
        valgfri(Behandling.STEG)

        påkrevd(Sak.AKTOR_ID)
        påkrevd(Sak.FAGSYSTEM, visPåOppgave = false)
        påkrevd(Sak.SAKSNUMMER)
        valgfri(Sak.MOTTATT_DATO)
        valgfri(Sak.TID_SIDEN_MOTTATT_DATO)
        valgfri(Sak.REGISTRERT_DATO)

        påkrevd(Vedtak.YTELSESTYPE)
        valgfri(Vedtak.BEHANDLENDE_ENHET)

        valgfri(Aksjonspunkt.ALLE)
        valgfri(Aksjonspunkt.AKTIVE)
        valgfri(Aksjonspunkt.UTFORT)
        valgfri(Aksjonspunkt.AVBRUTT)

        valgfri(Saksbehandling.ANSVARLIG_SAKSBEHANDLER)
        valgfri(Saksbehandling.ANSVARLIG_BESLUTTER)
        valgfri(Saksbehandling.LIGGER_HOS_BESLUTTER)
        valgfri(Saksbehandling.TID_FORSTE_GANG_HOS_BESLUTTER)
        valgfri(Saksbehandling.HELAUTOMATISK)
    }

    /**
     * UPY-oppgaver skal ikke vises for saksbehandlere, og har derfor ingen oppgavebehandlings-URL.
     */
    val ORDINÆR = Oppgavetypedefinisjon(UngOppgavetypenavn.UNGDOMSPROGRAMYTELSENORDINÆR.kode, behandlingsUrl = null) {
        inkluder(fellesfelter)
        valgfri(Aksjonspunkt.LOSBART)
        valgfri(Aksjonspunkt.FREMTIDIG)
        valgfri(Behandling.ARSAK)
        påkrevd(Behandling.RESULTATTYPE)
        valgfri(Soknad.NYE_KRAV)
        valgfri(Vedtak.DATO)
        påkrevd(Vedtak.TOTRINNSKONTROLL)
        valgfri(Ventetid.AKTIV_ARSAK)
        valgfri(Ventetid.AKTIV_FRIST)
    }

    val KLAGE = Oppgavetypedefinisjon(UngOppgavetypenavn.UNGDOMSPROGRAMYTELSENKLAGE.kode, behandlingsUrl = null) {
        inkluder(fellesfelter)
        valgfri(Aksjonspunkt.LOSBART)
        valgfri(Aksjonspunkt.FREMTIDIG)
        valgfri(Behandling.ARSAK)
        påkrevd(Behandling.RESULTATTYPE)
        valgfri(Soknad.NYE_KRAV)
        valgfri(Vedtak.DATO)
        påkrevd(Vedtak.TOTRINNSKONTROLL)
        valgfri(Ventetid.AKTIV_ARSAK)
        valgfri(Ventetid.AKTIV_FRIST)
    }

    val TILBAKE = Oppgavetypedefinisjon(UngOppgavetypenavn.UNGDOMSPROGRAMYTELSENTILBAKE.kode, behandlingsUrl = null) {
        inkluder(fellesfelter)
        valgfri(UngdomsprogramytelsenFelter.Tilbake.FORSTE_FEILUTBETALING)
        valgfri(UngdomsprogramytelsenFelter.Tilbake.FEILUTBETALT_BELOP)
        valgfri(UngdomsprogramytelsenFelter.Tilbake.LOSBARE)
        valgfri(UngdomsprogramytelsenFelter.Tilbake.UTFORTE)
        valgfri(UngdomsprogramytelsenFelter.Tilbake.AVBRUTTE)
    }

    override val oppgavetyper = listOf(ORDINÆR, KLAGE, TILBAKE)

    fun hent(oppgavetype: UngOppgavetypenavn) = hent(oppgavetype.kode)
}
