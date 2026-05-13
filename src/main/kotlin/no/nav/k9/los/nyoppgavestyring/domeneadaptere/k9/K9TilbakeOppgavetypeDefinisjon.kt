package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9

import no.nav.k9.los.nyoppgavestyring.feltutlederforlagring.FerdigstiltEnhet
import no.nav.k9.los.nyoppgavestyring.feltutlederforlagring.FerdigstiltTidspunkt
import no.nav.k9.los.nyoppgavestyring.feltutlederforlagring.GyldigeFeltutledere
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjoner
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavefelt
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavetype
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavetyper

object K9TilbakeOppgavetypeDefinisjon {
    const val DEFINISJONSKILDE = "k9-tilbake-til-los"

    fun lagOppgavetyper(
        frontendUrl: String,
        område: Område,
        feltdefinisjoner: Feltdefinisjoner,
        gyldigeFeltutledere: GyldigeFeltutledere
    ): Oppgavetyper {
        return Oppgavetyper(
            område = område,
            oppgavetyper = setOf(
                Oppgavetype(
                    eksternId = K9Oppgavetypenavn.TILBAKE.kode,
                    område = område,
                    definisjonskilde = DEFINISJONSKILDE,
                    oppgavebehandlingsUrlTemplate = "$frontendUrl/fagsak/{${K9FeltIder.SAKSNUMMER}}/",
                    oppgavefelter = setOf(
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.BEHANDLING_UUID),
                            visPåOppgave = true,
                            påkrevd = true,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.AKTOR_ID),
                            visPåOppgave = true,
                            påkrevd = true,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.FAGSYSTEM),
                            visPåOppgave = false,
                            påkrevd = true,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.SAKSNUMMER),
                            visPåOppgave = true,
                            påkrevd = true,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.FORSTE_FEILUTBETALING_DATO),
                            visPåOppgave = true,
                            påkrevd = false,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.FEILUTBETALT_BELOP),
                            visPåOppgave = true,
                            påkrevd = false,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.MOTTATT_DATO),
                            visPåOppgave = true,
                            påkrevd = false,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.REGISTRERT_DATO),
                            visPåOppgave = true,
                            påkrevd = false,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.YTELSESTYPE),
                            visPåOppgave = true,
                            påkrevd = true,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.BEHANDLINGSSTATUS),
                            visPåOppgave = true,
                            påkrevd = true,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.BEHANDLING_TYPEKODE),
                            visPåOppgave = true,
                            påkrevd = true,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.BEHANDLENDE_ENHET),
                            visPåOppgave = true,
                            påkrevd = false,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.ANSVARLIG_SAKSBEHANDLER),
                            visPåOppgave = true,
                            påkrevd = false,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.ANSVARLIG_BESLUTTER),
                            visPåOppgave = true,
                            påkrevd = false,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.LIGGER_HOS_BESLUTTER),
                            visPåOppgave = true,
                            påkrevd = false,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.TID_FORSTE_GANG_HOS_BESLUTTER),
                            visPåOppgave = true,
                            påkrevd = false,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.LOSBART_AKSJONSPUNKT),
                            visPåOppgave = true,
                            påkrevd = false,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.UTFORT_AKSJONSPUNKT),
                            visPåOppgave = true,
                            påkrevd = false,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.AVBRUTT_AKSJONSPUNKT),
                            visPåOppgave = true,
                            påkrevd = false,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.BEHANDLINGSSTEG),
                            visPåOppgave = false,
                            påkrevd = false,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.RESULTATTYPE),
                            visPåOppgave = false,
                            påkrevd = false,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.HELAUTOMATISK_BEHANDLET),
                            visPåOppgave = false,
                            påkrevd = false,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.FERDIGSTILT_TIDSPUNKT),
                            visPåOppgave = false,
                            påkrevd = false,
                            defaultverdi = null,
                            feltutleder = FerdigstiltTidspunkt
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.FERDIGSTILT_ENHET),
                            visPåOppgave = false,
                            påkrevd = false,
                            defaultverdi = null,
                            feltutleder = gyldigeFeltutledere.hentFeltutleder(FerdigstiltEnhet::class.java.canonicalName)
                        )
                    )
                )
            )
        )
    }
}
