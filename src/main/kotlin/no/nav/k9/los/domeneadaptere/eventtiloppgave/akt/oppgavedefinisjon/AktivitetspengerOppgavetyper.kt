package no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.oppgavedefinisjon

import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.kodeverk.AktOppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.oppgavedefinisjon.AktivitetspengerFelter.Aksjonspunkt
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.oppgavedefinisjon.AktivitetspengerFelter.Behandling
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.oppgavedefinisjon.AktivitetspengerFelter.Sak
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.oppgavedefinisjon.AktivitetspengerFelter.Saksbehandling
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.oppgavedefinisjon.AktivitetspengerFelter.Soknad
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.oppgavedefinisjon.AktivitetspengerFelter.Vedtak
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.oppgavedefinisjon.AktivitetspengerFelter.Ventetid
import no.nav.k9.los.oppgavedefinisjon.typesikker.Oppgavefeltsett
import no.nav.k9.los.oppgavedefinisjon.typesikker.Oppgavetypedefinisjon
import no.nav.k9.los.oppgavedefinisjon.typesikker.Oppgavetypekatalog

object AktivitetspengerOppgavetyper : Oppgavetypekatalog(AktivitetspengerFelter) {

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

    private fun fagsakUrl(frontendUrl: String) = "$frontendUrl/fagsak/{${Sak.SAKSNUMMER.id}}/"

    val ORDINÆR_DEL1 = Oppgavetypedefinisjon(AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL1.kode, ::fagsakUrl) {
        inkluder(fellesfelter)
    }

    val ORDINÆR_DEL2 = Oppgavetypedefinisjon(AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL2.kode, ::fagsakUrl) {
        inkluder(fellesfelter)
    }

    //TODO: klage, feilutbetaling
    override val oppgavetyper = listOf(ORDINÆR_DEL1, ORDINÆR_DEL2)

    fun hent(oppgavetype: AktOppgavetypenavn) = hent(oppgavetype.kode)
}

