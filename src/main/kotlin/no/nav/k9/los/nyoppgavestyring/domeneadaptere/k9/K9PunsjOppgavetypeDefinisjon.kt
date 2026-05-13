package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9

import no.nav.k9.los.nyoppgavestyring.feltutlederforlagring.FerdigstiltEnhet
import no.nav.k9.los.nyoppgavestyring.feltutlederforlagring.FerdigstiltTidspunkt
import no.nav.k9.los.nyoppgavestyring.feltutlederforlagring.GyldigeFeltutledere
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjoner
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavefelt
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavetype
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavetyper

object K9PunsjOppgavetypeDefinisjon {
    const val DEFINISJONSKILDE = "k9-punsj-til-los"

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
                    eksternId = K9Oppgavetypenavn.PUNSJ.kode,
                    område = område,
                    definisjonskilde = DEFINISJONSKILDE,
                    oppgavebehandlingsUrlTemplate = "$frontendUrl/journalpost/{${K9FeltIder.JOURNALPOST_ID}}/",
                    oppgavefelter = setOf(
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.AKTOR_ID),
                            visPåOppgave = true,
                            påkrevd = false,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.PLEIETRENGENDE_AKTOR_ID),
                            visPåOppgave = true,
                            påkrevd = false,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.BEHANDLING_TYPEKODE),
                            visPåOppgave = true,
                            påkrevd = false,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.HELAUTOMATISK_BEHANDLET),
                            visPåOppgave = true,
                            påkrevd = false,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.YTELSESTYPE),
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
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.JOURNALFORT),
                            visPåOppgave = true,
                            påkrevd = false,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.JOURNALFORT_TIDSPUNKT),
                            visPåOppgave = true,
                            påkrevd = false,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.JOURNALPOST_ID),
                            visPåOppgave = true,
                            påkrevd = false,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.REGISTRERT_DATO),
                            visPåOppgave = true,
                            påkrevd = true,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.MOTTATT_DATO),
                            visPåOppgave = true,
                            påkrevd = false,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(K9FeltIder.FERDIGSTILT_TIDSPUNKT),
                            visPåOppgave = false,
                            påkrevd = false,
                            defaultverdi = null,
                            feltutleder = gyldigeFeltutledere.hentFeltutleder(FerdigstiltTidspunkt::class.java.canonicalName)
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
