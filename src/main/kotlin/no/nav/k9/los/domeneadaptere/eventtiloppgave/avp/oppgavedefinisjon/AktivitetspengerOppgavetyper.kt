package no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.oppgavedefinisjon

import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.kodeverk.AvpOppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.oppgavedefinisjon.AktivitetspengerFelter.Aksjonspunkt
import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.oppgavedefinisjon.AktivitetspengerFelter.Behandling
import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.oppgavedefinisjon.AktivitetspengerFelter.Sak
import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.oppgavedefinisjon.AktivitetspengerFelter.Saksbehandling
import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.oppgavedefinisjon.AktivitetspengerFelter.Soknad
import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.oppgavedefinisjon.AktivitetspengerFelter.Vedtak
import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.oppgavedefinisjon.AktivitetspengerFelter.Ventetid
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
        påkrevd(Sak.MOTTATT_DATO)
        valgfri(Sak.TID_SIDEN_MOTTATT_DATO)
        valgfri(Sak.REGISTRERT_DATO)

        valgfri(Vedtak.DATO)
        påkrevd(Vedtak.YTELSESTYPE)
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
        påkrevd(Saksbehandling.BEHANDLENDE_ENHET)
        påkrevd(Saksbehandling.HELAUTOMATISK)
        valgfri(Saksbehandling.UTENLANDSSAK) //TODO: mangler fra ung-sak

        valgfri(Ventetid.AKTIV_ARSAK)
        valgfri(Ventetid.AKTIV_FRIST)
    }

    private fun fagsakUrl(frontendUrl: String) = "$frontendUrl/fagsak/{${Sak.SAKSNUMMER.id}}/"

    val ORDINÆR_DEL1 = Oppgavetypedefinisjon(AvpOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL1.kode, ::fagsakUrl) {
        inkluder(fellesfelter)
    }

    val ORDINÆR_DEL2 = Oppgavetypedefinisjon(AvpOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL2.kode, ::fagsakUrl) {
        inkluder(fellesfelter)
    }

    //TODO: klage, feilutbetaling
    override val oppgavetyper = listOf(ORDINÆR_DEL1, ORDINÆR_DEL2)

    fun hent(oppgavetype: AvpOppgavetypenavn) = hent(oppgavetype.kode)
}

