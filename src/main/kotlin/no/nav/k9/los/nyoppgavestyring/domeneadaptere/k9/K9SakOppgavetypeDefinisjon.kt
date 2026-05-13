package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9

import no.nav.k9.los.nyoppgavestyring.feltutlederforlagring.*
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjoner
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavefelt
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavetype
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavetyper

object K9SakOppgavetypeDefinisjon {
    const val DEFINISJONSKILDE = "k9-sak-til-los"

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
                    eksternId = K9Oppgavetypenavn.SAK.kode,
                    område = område,
                    definisjonskilde = DEFINISJONSKILDE,
                    oppgavebehandlingsUrlTemplate = "$frontendUrl/fagsak/{K9.saksnummer}/",
                    oppgavefelter = setOf(
                        oppgavefelt(feltdefinisjoner, K9FeltIder.BEHANDLING_UUID, visPåOppgave = true, påkrevd = true),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.AKTOR_ID, visPåOppgave = true, påkrevd = true),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.FAGSYSTEM, visPåOppgave = false, påkrevd = true),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.SAKSNUMMER, visPåOppgave = true, påkrevd = true),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.RESULTATTYPE, visPåOppgave = true, påkrevd = true),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.BEHANDLINGSSTEG, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.MOTTATT_DATO, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.TID_SIDEN_MOTTATT_DATO, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.REGISTRERT_DATO, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.VEDTAKSDATO, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.YTELSESTYPE, visPåOppgave = true, påkrevd = true),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.BEHANDLINGSSTATUS, visPåOppgave = true, påkrevd = true),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.BEHANDLING_TYPEKODE, visPåOppgave = true, påkrevd = true),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.RELATERT_PART_AKTORID, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.BEHANDLENDE_ENHET, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.PLEIETRENGENDE_AKTOR_ID, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.ANSVARLIG_SAKSBEHANDLER, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.ANSVARLIG_BESLUTTER, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.LIGGER_HOS_BESLUTTER, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.TID_FORSTE_GANG_HOS_BESLUTTER, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.TOTRINNSKONTROLL, visPåOppgave = true, påkrevd = true),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.AKSJONSPUNKT, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.AKTIVT_AKSJONSPUNKT, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.AKTIV_VENTEARSAK, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.AKTIV_VENTEFRIST, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.LOSBART_AKSJONSPUNKT, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.UTFORT_AKSJONSPUNKT, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.AVBRUTT_AKSJONSPUNKT, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.FREMTIDIG_AKSJONSPUNKT, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.AVVENTER_SOKER, visPåOppgave = false, påkrevd = true),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.AVVENTER_ARBEIDSGIVER, visPåOppgave = false, påkrevd = true),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.AVVENTER_SAKSBEHANDLER, visPåOppgave = false, påkrevd = true),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.AVVENTER_TEKNISK_FEIL, visPåOppgave = false, påkrevd = true),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.AVVENTER_ANNET, visPåOppgave = false, påkrevd = true),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.AVVENTER_ANNET_IKKE_SAKSBEHANDLINGSTID, visPåOppgave = false, påkrevd = true),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.AKKUMULERT_VENTETID_SAKSBEHANDLER_FOR_TIDLIGERE_VERSJONER, visPåOppgave = false, påkrevd = false,
                            feltutleder = gyldigeFeltutledere.hentFeltutleder(AkkumulertVentetidSaksbehandler::class.java.canonicalName)),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.AKKUMULERT_VENTETID_SOKER_FOR_TIDLIGERE_VERSJONER, visPåOppgave = false, påkrevd = false,
                            feltutleder = gyldigeFeltutledere.hentFeltutleder(AkkumulertVentetidSøker::class.java.canonicalName)),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.AKKUMULERT_VENTETID_TEKNISK_FEIL_FOR_TIDLIGERE_VERSJONER, visPåOppgave = false, påkrevd = false,
                            feltutleder = gyldigeFeltutledere.hentFeltutleder(AkkumulertVentetidTekniskFeil::class.java.canonicalName)),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.AKKUMULERT_VENTETID_ARBEIDSGIVER_FOR_TIDLIGERE_VERSJONER, visPåOppgave = false, påkrevd = false,
                            feltutleder = gyldigeFeltutledere.hentFeltutleder(AkkumulertVentetidArbeidsgiver::class.java.canonicalName)),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.AKKUMULERT_VENTETID_ANNET_FOR_TIDLIGERE_VERSJONER, visPåOppgave = false, påkrevd = false,
                            feltutleder = gyldigeFeltutledere.hentFeltutleder(AkkumulertVentetidAnnet::class.java.canonicalName)),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.AKKUMULERT_VENTETID_ANNET_IKKE_SAKSBEHANDLINGSTID_FOR_TIDLIGERE_VERSJONER, visPåOppgave = false, påkrevd = false,
                            feltutleder = gyldigeFeltutledere.hentFeltutleder(AkkumulertVentetidAnnetIkkeSaksbehandlingstid::class.java.canonicalName)),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.AKKUMULERT_VENTETID_ANNET_IKKE_SAKSBEHANDLINGSTID, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.AKKUMULERT_VENTETID_ANNET, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.AKKUMULERT_VENTETID_ARBEIDSGIVER, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.AKKUMULERT_VENTETID_SAKSBEHANDLER, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.AKKUMULERT_VENTETID_SOKER, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.AKKUMULERT_VENTETID_TEKNISK_FEIL, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.OPPGAVESAKSBEHANDLINGSTID, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.HELAUTOMATISK_BEHANDLET, visPåOppgave = false, påkrevd = true),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.NYE_KRAV, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.BARE_NYE_KRAV, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.ANTALL_OMSOKTE_DAGER, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.FRA_ENDRINGSDIALOG, visPåOppgave = false, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.HASTESAK, visPåOppgave = true, påkrevd = false, defaultverdi = "false"),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.UTENLANDSTILSNITT, visPåOppgave = true, påkrevd = true, defaultverdi = "false"),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.DIREKTEUTBETALING, visPåOppgave = true, påkrevd = true, defaultverdi = "false"),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.SOKNADSARSAK, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.BEHANDLINGSARSAK, visPåOppgave = true, påkrevd = false),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.FERDIGSTILT_TIDSPUNKT, visPåOppgave = false, påkrevd = false,
                            feltutleder = gyldigeFeltutledere.hentFeltutleder(FerdigstiltTidspunkt::class.java.canonicalName)),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.FERDIGSTILT_ENHET, visPåOppgave = false, påkrevd = false,
                            feltutleder = gyldigeFeltutledere.hentFeltutleder(FerdigstiltEnhet::class.java.canonicalName)),
                        oppgavefelt(feltdefinisjoner, K9FeltIder.FAGSAK_AR, visPåOppgave = true, påkrevd = false),
                    )
                )
            )
        )
    }

    private fun oppgavefelt(
        feltdefinisjoner: Feltdefinisjoner,
        feltId: String,
        visPåOppgave: Boolean,
        påkrevd: Boolean,
        defaultverdi: String? = null,
        feltutleder: Feltutleder? = null
    ): Oppgavefelt {
        return Oppgavefelt(
            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon(feltId),
            visPåOppgave = visPåOppgave,
            påkrevd = påkrevd,
            defaultverdi = defaultverdi,
            feltutleder = feltutleder
        )
    }
}
