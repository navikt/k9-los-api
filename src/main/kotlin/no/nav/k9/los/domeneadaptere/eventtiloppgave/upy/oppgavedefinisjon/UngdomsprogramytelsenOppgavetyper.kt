package no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.oppgavedefinisjon

import no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.kodeverk.UpyOppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.oppgavedefinisjon.UngdomsprogramytelsenFelter.Aksjonspunkt
import no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.oppgavedefinisjon.UngdomsprogramytelsenFelter.Behandling
import no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.oppgavedefinisjon.UngdomsprogramytelsenFelter.Sak
import no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.oppgavedefinisjon.UngdomsprogramytelsenFelter.Saksbehandling
import no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.oppgavedefinisjon.UngdomsprogramytelsenFelter.Soknad
import no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.oppgavedefinisjon.UngdomsprogramytelsenFelter.Vedtak
import no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.oppgavedefinisjon.UngdomsprogramytelsenFelter.Ventetid
import no.nav.k9.los.oppgavedefinisjon.typesikker.Oppgavefeltsett
import no.nav.k9.los.oppgavedefinisjon.typesikker.Oppgavetypedefinisjon
import no.nav.k9.los.oppgavedefinisjon.typesikker.Oppgavetypekatalog

object UngdomsprogramytelsenOppgavetyper : Oppgavetypekatalog(UngdomsprogramytelsenFelter) {

    private val fellesfelter = Oppgavefeltsett {
        påkrevd(Behandling.UUID)
        påkrevd(Behandling.TYPEKODE)
        påkrevd(Behandling.STATUS)
        valgfri(Behandling.STEG)
        valgfri(Behandling.ARSAK)
        påkrevd(Behandling.RESULTATTYPE)

        valgfri(Soknad.NYE_KRAV)

        påkrevd(Sak.AKTOR_ID)
        påkrevd(Sak.FAGSYSTEM, visPåOppgave = false)
        påkrevd(Sak.SAKSNUMMER)
        valgfri(Sak.MOTTATT_DATO)
        valgfri(Sak.TID_SIDEN_MOTTATT_DATO)
        valgfri(Sak.REGISTRERT_DATO)

        valgfri(Vedtak.DATO)
        påkrevd(Vedtak.YTELSESTYPE)
        valgfri(Vedtak.BEHANDLENDE_ENHET)
        påkrevd(Vedtak.TOTRINNSKONTROLL)

        valgfri(Aksjonspunkt.ALLE)
        valgfri(Aksjonspunkt.AKTIVE)
        valgfri(Aksjonspunkt.LOSBART)
        valgfri(Aksjonspunkt.UTFORT)
        valgfri(Aksjonspunkt.AVBRUTT)
        valgfri(Aksjonspunkt.FREMTIDIG)

        valgfri(Saksbehandling.ANSVARLIG_SAKSBEHANDLER)
        valgfri(Saksbehandling.ANSVARLIG_BESLUTTER)
        valgfri(Saksbehandling.LIGGER_HOS_BESLUTTER)
        valgfri(Saksbehandling.TID_FORSTE_GANG_HOS_BESLUTTER)

        valgfri(Ventetid.AKTIV_ARSAK)
        valgfri(Ventetid.AKTIV_FRIST)
    }

    /**
     * UPY-oppgaver skal ikke vises for saksbehandlere, og har derfor ingen oppgavebehandlings-URL.
     */
    val ORDINÆR = Oppgavetypedefinisjon(UpyOppgavetypenavn.UNGDOMSPROGRAMYTELSENORDINÆR.kode, behandlingsUrl = null) {
        inkluder(fellesfelter)
    }

    val KLAGE = Oppgavetypedefinisjon(UpyOppgavetypenavn.UNGDOMSPROGRAMYTELSENKLAGE.kode, behandlingsUrl = null) {
        inkluder(fellesfelter)
    }

    //TODO: feilutbetaling
    override val oppgavetyper = listOf(ORDINÆR, KLAGE)

    fun hent(oppgavetype: UpyOppgavetypenavn) = hent(oppgavetype.kode)
}

