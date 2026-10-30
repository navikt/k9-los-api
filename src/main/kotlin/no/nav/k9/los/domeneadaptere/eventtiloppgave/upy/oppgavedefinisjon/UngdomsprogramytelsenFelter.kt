package no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.oppgavedefinisjon

import no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.kodeverk.UpyBehandlendeEnhet
import no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.kodeverk.UpyFagsystem
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.Synlighet.INTERNT
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.Synlighet.OVER_STREKEN
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.Synlighet.UNDER_STREKEN
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavedefinisjon.typesikker.Feltgruppe
import no.nav.k9.los.oppgavedefinisjon.typesikker.Feltkatalog
import no.nav.k9.los.oppgaveuthenting.query.mapping.transientfeltutleder.UngdomsprogramytelsenTidSidenMottattDatoUtleder
import no.nav.ung.kodeverk.behandling.BehandlingResultatType
import no.nav.ung.kodeverk.behandling.BehandlingStatus
import no.nav.ung.kodeverk.behandling.BehandlingStegType
import no.nav.ung.kodeverk.behandling.BehandlingType
import no.nav.ung.kodeverk.behandling.BehandlingÅrsakType
import no.nav.ung.kodeverk.behandling.FagsakYtelseType
import no.nav.ung.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.ung.kodeverk.behandling.aksjonspunkt.Venteårsak

object UngdomsprogramytelsenFelter : Feltkatalog(Områder.UNGDOMSPROGRAMYTELSEN) {
    override val grupper get() = listOf(Behandling, Soknad, Sak, Vedtak, Aksjonspunkt, Saksbehandling, Ventetid)

    object Behandling : Feltgruppe() {
        val UUID = string("behandlingUuid", "Behandling", INTERNT)
        val TYPEKODE = kode("behandlingTypekode", "Behandlingstype", OVER_STREKEN, BehandlingType::class)
        val STATUS = kode("behandlingsstatus", "Behandlingsstatus", UNDER_STREKEN, BehandlingStatus::class)
        val STEG = string("behandlingssteg", "Behandlingssteg", UNDER_STREKEN, kodeverk = BehandlingStegType::class)
        val ARSAK = stringliste("behandlingsårsak", "Behandlingsårsak", OVER_STREKEN, kodeverk = BehandlingÅrsakType::class)
        val RESULTATTYPE = string("resultattype", "Resultattype", UNDER_STREKEN, kodeverk = BehandlingResultatType::class)
    }

    object Soknad : Feltgruppe() {
        val NYE_KRAV = boolean("nyeKrav", "Søknad inneholder nye perioder", OVER_STREKEN)
    }

    object Sak : Feltgruppe() {
        val AKTOR_ID = string("aktorId", "Søkers aktør-ID", INTERNT)
        val FAGSYSTEM = string("fagsystem", "Fagsystem", UNDER_STREKEN, kodeverk = UpyFagsystem::class)
        val SAKSNUMMER = string("saksnummer", "Saksnummer", UNDER_STREKEN)
        val MOTTATT_DATO = timestamp("mottattDato", "Mottatt dato", UNDER_STREKEN)
        val TID_SIDEN_MOTTATT_DATO = duration(
            "tidSidenMottattDato", "Dager siden mottatt dato", OVER_STREKEN,
            transientFeltutleder = UngdomsprogramytelsenTidSidenMottattDatoUtleder::class,
        )
        val REGISTRERT_DATO = timestamp("registrertDato", "Registrert dato", UNDER_STREKEN)
    }

    object Vedtak : Feltgruppe() {
        val DATO = dato("vedtaksdato", "Vedtaksdato", UNDER_STREKEN)
        val YTELSESTYPE = kode("ytelsestype", "Ytelsestype", OVER_STREKEN, FagsakYtelseType::class)
        val BEHANDLENDE_ENHET = string("behandlendeEnhet", "Behandlende enhet", UNDER_STREKEN, kodeverk = UpyBehandlendeEnhet::class)
        val TOTRINNSKONTROLL = boolean("totrinnskontroll", "Må gjennom totrinnskontroll", UNDER_STREKEN)
    }

    object Aksjonspunkt : Feltgruppe() {
        val ALLE = kodeliste(
            "aksjonspunkt", "Løsbare, fremtidige og tidligere aksjonspunkt", UNDER_STREKEN, AksjonspunktDefinisjon::class,
            beskrivelse = "Løsbare- (se aksjonspunkt som kan løses), fremtidige- (se Løsbare og fremtidige aksjonspunkt) og løste aksjonspunkt",
        )
        val AKTIVE = kodeliste(
            "aktivtAksjonspunkt", "Løsbare og fremtidige aksjonspunkt", UNDER_STREKEN, AksjonspunktDefinisjon::class,
            beskrivelse = "Løsbare aksjonspunkt (se aksjonspunkt som kan løses) og åpne aksjonspunkter som kan løses først når behandlingen når riktig steg",
        )
        val LOSBART = kode(
            "løsbartAksjonspunkt", "Aksjonspunkt: løsbart", OVER_STREKEN, AksjonspunktDefinisjon::class,
            beskrivelse = "Åpne aksjonspunkt som er løsbart akkurat nå, fordi de hører til steget behandlingen står i",
        )
        val UTFORT = kodeliste(
            "utførtAksjonspunkt", "Aksjonspunkt: utførte", UNDER_STREKEN, AksjonspunktDefinisjon::class,
            beskrivelse = "Aksjonspunkter som er utført",
        )
        val AVBRUTT = kodeliste(
            "avbruttAksjonspunkt", "Aksjonspunkt: avbrutte", UNDER_STREKEN, AksjonspunktDefinisjon::class,
            beskrivelse = "Aksjonspunkter som er avbrutt",
        )
        val FREMTIDIG = kodeliste(
            "fremtidigAksjonspunkt", "Aksjonspunkt: fremtidige", UNDER_STREKEN, AksjonspunktDefinisjon::class,
            beskrivelse = "Åpne aksjonspunkter som ikke kan løses i steget behandlingen står i nå",
        )
    }

    object Saksbehandling : Feltgruppe() {
        val ANSVARLIG_SAKSBEHANDLER = string("ansvarligSaksbehandler", "Ansvarlig saksbehandler", UNDER_STREKEN)
        val ANSVARLIG_BESLUTTER = string("ansvarligBeslutter", "Ansvarlig beslutter", UNDER_STREKEN)
        val LIGGER_HOS_BESLUTTER = boolean("liggerHosBeslutter", "Til beslutter", OVER_STREKEN)
        val TID_FORSTE_GANG_HOS_BESLUTTER = timestamp(
            "tidFørsteGangHosBeslutter", "Tidspunkt oppgaven gikk til beslutter første gang", OVER_STREKEN,
        )
    }

    object Ventetid : Feltgruppe() {
        val AKTIV_ARSAK = kode("aktivVenteårsak", "Aktiv venteårsak", UNDER_STREKEN, Venteårsak::class)
        val AKTIV_FRIST = timestamp("aktivVentefrist", "Aktiv ventefrist", UNDER_STREKEN)
    }
}

