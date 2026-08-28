package no.nav.k9.los.domeneadaptere.ung.akt.oppgavedefinisjon

import no.nav.k9.los.kodeverk.AktFagsystem
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.Datatype
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.FeltdefinisjonDto
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.FeltdefinisjonerDto
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.KodeverkReferanseDto
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.Synlighet
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.mapping.transientfeltutleder.K9SakTidSidenMottattDatoUtleder
import no.nav.k9.los.oppgaveuthenting.query.mapping.transientfeltutleder.TransientFeltutleder
import no.nav.ung.kodeverk.behandling.BehandlingStatus
import no.nav.ung.kodeverk.behandling.BehandlingStegType
import no.nav.ung.kodeverk.behandling.BehandlingType
import no.nav.ung.kodeverk.behandling.BehandlingÅrsakType
import kotlin.reflect.KClass

object AktivitetspengerFeltdefinisjoner {
    fun lagFeltdefinisjoner(): FeltdefinisjonerDto {
        return FeltdefinisjonerDto(
            område = Områder.AKTIVITETSPENGER,
            feltdefinisjoner = setOf(
                // Behandling
                felt(
                    id = AktivitetspengerFeltIder.Behandling.UUID,
                    visningsnavn = "Behandling",
                    listetype = false,
                    tolkesSom = Datatype.STRING,
                    synlighet = Synlighet.INTERNT,
                ),
                felt(
                    id = AktivitetspengerFeltIder.Behandling.TYPEKODE,
                    visningsnavn = "Behandlingstype",
                    listetype = false,
                    tolkesSom = Datatype.STRING,
                    synlighet = Synlighet.OVER_STREKEN,
                    kodeverkreferanse = kodeverk(Områder.AKTIVITETSPENGER, BehandlingType::class.java.simpleName),
                ),
                felt(
                    id = AktivitetspengerFeltIder.Behandling.STATUS,
                    visningsnavn = "Behandlingsstatus",
                    listetype = false,
                    tolkesSom = Datatype.STRING,
                    synlighet = Synlighet.UNDER_STREKEN,
                    kodeverkreferanse = kodeverk(Områder.AKTIVITETSPENGER, BehandlingStatus::class.java.simpleName),
                ),
                felt(
                    id = AktivitetspengerFeltIder.Behandling.STEG,
                    visningsnavn = "Behandlingssteg",
                    listetype = false,
                    tolkesSom = Datatype.STRING,
                    synlighet = Synlighet.UNDER_STREKEN,
                    kodeverkreferanse = kodeverk(Områder.AKTIVITETSPENGER, BehandlingStegType::class.java.simpleName),
                ),
                felt(
                    id = AktivitetspengerFeltIder.Behandling.ARSAK,
                    visningsnavn = "Behandlingsårsak",
                    listetype = true,
                    tolkesSom = Datatype.STRING,
                    synlighet = Synlighet.OVER_STREKEN,
                    kodeverkreferanse = kodeverk(Områder.AKTIVITETSPENGER, BehandlingÅrsakType::class.java.simpleName),
                ),
                // Soknad
                felt(
                    id = AktivitetspengerFeltIder.Soknad.NYE_KRAV,
                    visningsnavn = "Søknad inneholder nye perioder",
                    listetype = false,
                    tolkesSom = Datatype.BOOLEAN,
                    synlighet = Synlighet.OVER_STREKEN,
                ),
                // Sak
                felt(
                    id = AktivitetspengerFeltIder.Sak.AKTOR_ID,
                    visningsnavn = "Søkers aktør-ID",
                    listetype = false,
                    tolkesSom = Datatype.STRING,
                    synlighet = Synlighet.INTERNT,
                ),
                felt(
                    id = AktivitetspengerFeltIder.Sak.FAGSYSTEM,
                    visningsnavn = "Fagsystem",
                    listetype = false,
                    tolkesSom = Datatype.STRING,
                    synlighet = Synlighet.UNDER_STREKEN,
                    kodeverkreferanse = kodeverk(Områder.AKTIVITETSPENGER, AktFagsystem::class.java.simpleName),
                ),
                felt(
                    id = AktivitetspengerFeltIder.Sak.SAKSNUMMER,
                    visningsnavn = "Saksnummer",
                    listetype = false,
                    tolkesSom = Datatype.STRING,
                    synlighet = Synlighet.UNDER_STREKEN,
                ),
                felt(
                    id = AktivitetspengerFeltIder.Sak.MOTTATT_DATO,
                    visningsnavn = "Mottatt dato",
                    listetype = false,
                    tolkesSom = Datatype.TIMESTAMP,
                    synlighet = Synlighet.UNDER_STREKEN,
                ),
                felt(
                    id = AktivitetspengerFeltIder.Sak.TID_SIDEN_MOTTATT_DATO,
                    visningsnavn = "Dager siden mottatt dato",
                    listetype = false,
                    tolkesSom = Datatype.DURATION,
                    synlighet = Synlighet.OVER_STREKEN,
                    transientFeltutleder = K9SakTidSidenMottattDatoUtleder::class,
                ),
                // Vedtak
                felt(
                    id = AktivitetspengerFeltIder.Vedtak.DATO,
                    visningsnavn = "Vedtaksdato",
                    listetype = false,
                    tolkesSom = Datatype.TIMESTAMP,
                    synlighet = Synlighet.UNDER_STREKEN,
                ),
                felt(
                    id = AktivitetspengerFeltIder.Vedtak.RESULTATTYPE,
                    visningsnavn = "Resultattype",
                    listetype = false,
                    tolkesSom = Datatype.STRING,
                    synlighet = Synlighet.UNDER_STREKEN,
                    kodeverkreferanse = kodeverk(Områder.AKTIVITETSPENGER, "Resultattype"),
                ),
                felt(
                    id = AktivitetspengerFeltIder.Vedtak.YTELSESTYPE,
                    visningsnavn = "Ytelsestype",
                    listetype = false,
                    tolkesSom = Datatype.STRING,
                    synlighet = Synlighet.OVER_STREKEN,
                    kodeverkreferanse = kodeverk(Områder.AKTIVITETSPENGER, "Ytelsetype"),
                ),
                felt(
                    id = AktivitetspengerFeltIder.Vedtak.BEHANDLENDE_ENHET,
                    visningsnavn = "Behandlende enhet",
                    listetype = false,
                    tolkesSom = Datatype.STRING,
                    synlighet = Synlighet.UNDER_STREKEN,
                    kodeverkreferanse = kodeverk(Områder.AKTIVITETSPENGER, "behandlendeEnhet"),
                ),
                felt(
                    id = AktivitetspengerFeltIder.Vedtak.TOTRINNSKONTROLL,
                    visningsnavn = "Må gjennom totrinnskontroll",
                    listetype = false,
                    tolkesSom = Datatype.BOOLEAN,
                    synlighet = Synlighet.UNDER_STREKEN,
                ),
                // Aksjonspunkt
                felt(
                    id = AktivitetspengerFeltIder.Aksjonspunkt.ALLE,
                    visningsnavn = "Løsbare, fremtidige og tidligere aksjonspunkt",
                    beskrivelse = "Løsbare- (se aksjonspunkt som kan løses), fremtidige- (se Løsbare og fremtidige aksjonspunkt) og løste aksjonspunkt",
                    listetype = true,
                    tolkesSom = Datatype.STRING,
                    synlighet = Synlighet.UNDER_STREKEN,
                    kodeverkreferanse = kodeverk(Områder.AKTIVITETSPENGER, "Aksjonspunkt"),
                ),
                felt(
                    id = AktivitetspengerFeltIder.Aksjonspunkt.AKTIVE,
                    visningsnavn = "Løsbare og fremtidige aksjonspunkt",
                    beskrivelse = "Løsbare aksjonspunkt (se aksjonspunkt som kan løses) og åpne aksjonspunkter som kan løses først når behandlingen når riktig steg",
                    listetype = true,
                    tolkesSom = Datatype.STRING,
                    synlighet = Synlighet.UNDER_STREKEN,
                    kodeverkreferanse = kodeverk(Områder.AKTIVITETSPENGER, "Aksjonspunkt"),
                ),
                felt(
                    id = AktivitetspengerFeltIder.Aksjonspunkt.LOSBART,
                    visningsnavn = "Aksjonspunkt: løsbart",
                    beskrivelse = "Åpne aksjonspunkt som er løsbart akkurat nå, fordi de hører til steget behandlingen står i",
                    listetype = false,
                    tolkesSom = Datatype.STRING,
                    synlighet = Synlighet.OVER_STREKEN,
                    kodeverkreferanse = kodeverk(Områder.AKTIVITETSPENGER, "Aksjonspunkt"),
                ),
                felt(
                    id = AktivitetspengerFeltIder.Aksjonspunkt.UTFORT,
                    visningsnavn = "Aksjonspunkt: utførte",
                    beskrivelse = "Aksjonspunkter som er utført",
                    listetype = true,
                    tolkesSom = Datatype.STRING,
                    synlighet = Synlighet.UNDER_STREKEN,
                    kodeverkreferanse = kodeverk(Områder.AKTIVITETSPENGER, "Aksjonspunkt"),
                ),
                felt(
                    id = AktivitetspengerFeltIder.Aksjonspunkt.AVBRUTT,
                    visningsnavn = "Aksjonspunkt: avbrutte",
                    beskrivelse = "Aksjonspunkter som er avbrutt",
                    listetype = true,
                    tolkesSom = Datatype.STRING,
                    synlighet = Synlighet.UNDER_STREKEN,
                    kodeverkreferanse = kodeverk(Områder.AKTIVITETSPENGER, "Aksjonspunkt"),
                ),
                felt(
                    id = AktivitetspengerFeltIder.Aksjonspunkt.FREMTIDIG,
                    visningsnavn = "Aksjonspunkt: fremtidige",
                    beskrivelse = "Åpne aksjonspunkter som ikke kan løses i steget behandlingen står i nå",
                    listetype = true,
                    tolkesSom = Datatype.STRING,
                    synlighet = Synlighet.UNDER_STREKEN,
                    kodeverkreferanse = kodeverk(Områder.AKTIVITETSPENGER, "Aksjonspunkt"),
                ),
                // Beslutter
                felt(
                    id = AktivitetspengerFeltIder.Beslutter.ANSVARLIG,
                    visningsnavn = "Ansvarlig beslutter",
                    listetype = false,
                    tolkesSom = Datatype.STRING,
                    synlighet = Synlighet.UNDER_STREKEN,
                ),
                felt(
                    id = AktivitetspengerFeltIder.Beslutter.LIGGER_HOS,
                    visningsnavn = "Til beslutter",
                    listetype = false,
                    tolkesSom = Datatype.BOOLEAN,
                    synlighet = Synlighet.OVER_STREKEN,
                ),
                felt(
                    id = AktivitetspengerFeltIder.Beslutter.TID_FORSTE_GANG_HOS,
                    visningsnavn = "Tidspunkt oppgaven gikk til beslutter første gang",
                    listetype = false,
                    tolkesSom = Datatype.TIMESTAMP,
                    synlighet = Synlighet.OVER_STREKEN,
                ),
                // Ventetid
                felt(
                    id = AktivitetspengerFeltIder.Ventetid.AKTIV_ARSAK,
                    visningsnavn = "Aktiv venteårsak",
                    listetype = false,
                    tolkesSom = Datatype.STRING,
                    synlighet = Synlighet.UNDER_STREKEN,
                    kodeverkreferanse = kodeverk(Områder.K9, "Venteårsak"),
                ),
                felt(
                    id = AktivitetspengerFeltIder.Ventetid.AKTIV_FRIST,
                    visningsnavn = "Aktiv ventefrist",
                    listetype = false,
                    tolkesSom = Datatype.TIMESTAMP,
                    synlighet = Synlighet.UNDER_STREKEN,
                ),
            )
        )
    }

    private fun felt(
        id: String,
        visningsnavn: String,
        listetype: Boolean,
        tolkesSom: Datatype,
        synlighet: Synlighet,
        beskrivelse: String? = null,
        kodeverkreferanse: KodeverkReferanseDto? = null,
        transientFeltutleder: KClass<out TransientFeltutleder>? = null,
    ) = FeltdefinisjonDto(
        id = id,
        visningsnavn = visningsnavn,
        beskrivelse = beskrivelse,
        listetype = listetype,
        tolkesSom = tolkesSom,
        synlighet = synlighet,
        kodeverkreferanse = kodeverkreferanse,
        transientFeltutleder = transientFeltutleder?.java?.canonicalName,
    )

    private fun kodeverk(omrade: Områder, eksternId: String) =
        KodeverkReferanseDto(område = omrade, eksternId = eksternId)
}

