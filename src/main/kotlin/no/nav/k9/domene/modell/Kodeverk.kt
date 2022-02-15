package no.nav.k9.domene.modell

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonValue
import no.nav.k9.domene.lager.oppgave.Kodeverdi

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
enum class AndreKriterierType(override val kode: String, override val navn: String) : Kodeverdi {
    FRA_PUNSJ("FRA_PUNSJ", "Fra Punsj"),
    TIL_BESLUTTER("TIL_BESLUTTER", "Til beslutter"),
    AVKLAR_MEDLEMSKAP("AVKLAR_MEDLEMSKAP", "Avklar medlemskap"),
    AVKLAR_INNTEKTSMELDING_BEREGNING("AVKLAR_INNTEKTSMELDING_BEREGNING", "Avklar inntektsmeldng"),
    VENTER_PÅ_KOMPLETT_SØKNAD("VENTER_PÅ_KOMPLETT_SØKNAD", "Venter på komplett søknad"),
    ENDELIG_BEH_AV_INNTEKTSMELDING("ENDELIG_BEH_AV_INNTEKTSMELDING", "Endelig beh av inntektsmelding"),
    VENTER_PÅ_ANNEN_PARTS_SAK("VENTER_PÅ_ANNEN_PARTS_SAK", "Venter på annen parts sak"),
    FORLENGELSER_FRA_INFOTRYGD("FORLENGELSER_FRA_INFOTRYGD", "Forlengelser fra infotrygd"),
    FORLENGELSER_FRA_INFOTRYGD_AKSJONSPUNKT("FORLENGELSER_FRA_INFOTRYGD_AKSJONSPUNKT", "Forlengelser fra infotrygd aksjonspunkt");

    override val kodeverk = "ANDRE_KRITERIER_TYPE"

    companion object {
        @JsonCreator
        @JvmStatic
        fun fraKode(kode: String): AndreKriterierType {
            return values().find { it.kode == kode } ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
        }
    }
}

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
enum class FagsakYtelseType constructor(override val kode: String, override val navn: String) : Kodeverdi {
    PLEIEPENGER_SYKT_BARN("PSB", "Pleiepenger sykt barn"),
    OMSORGSPENGER("OMP", "Omsorgspenger"),
    OMSORGSDAGER("OMD", "Omsorgsdager: overføring"),
    FRISINN("FRISINN", "Frisinn"),
    PPN("PPN", "Pleiepenger i livets sluttfase"),
    OLP("OLP", "OLP"),
    OMSORGSPENGER_KS("OMP_KS", "Omsorgsdager: kronisk syk"),
    OMSORGSPENGER_MA("OMP_MA", "Omsorgsdager: midlertidig alene"),
    OMSORGSPENGER_AO("OMP_AO", "Omsorgsdager: alene om omsorg"),
    UKJENT("UKJENT", "Ukjent");

    override val kodeverk = "FAGSAK_YTELSE_TYPE"

    companion object {
        @JsonCreator
        @JvmStatic
        fun fraKode(kode: String): FagsakYtelseType {
            return values().find { it.kode == kode } ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
        }
    }
}


@JsonFormat(shape = JsonFormat.Shape.OBJECT)
enum class FagsakStatus(override val kode: String, override val navn: String) : Kodeverdi {
    OPPRETTET("OPPR", "Opprettet"),
    UNDER_BEHANDLING("UBEH", "Under behandling"),
    LØPENDE("LOP", "Løpende"),
    AVSLUTTET("AVSLU", "Avsluttet");

    override val kodeverk = "FAGSAK_STATUS"

    companion object {
        @JsonCreator
        @JvmStatic
        fun fraKode(kode: String): FagsakStatus = values().find { it.kode == kode } ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
    }
}

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
enum class BehandlingType(override val kode: String, override val navn: String, override val kodeverk: String) :
    Kodeverdi {
    FORSTEGANGSSOKNAD("BT-002", "Førstegangsbehandling", "ae0034"),
    KLAGE("BT-003", "Klage", "ae0058"),
    REVURDERING("BT-004", "Revurdering", "ae0028"),
    INNSYN("BT-006", "Innsyn", "ae0042"),
    TILBAKE("BT-007", "Tilbakekreving", "ae0203"),
    ANKE("BT-008", "Anke", "ae0046"),
    REVURDERING_TILBAKEKREVING("BT-009", "Tilbakekreving revurdering", "BT-009"),
    UNNTAKSBEHANDLING("BT-010", "Unntaksbehandling", "N/A"),

    //gjelder punsj
    PAPIRSØKNAD("PAPIRSØKNAD", "Papirsøknad", "PUNSJ_INNSENDING_TYPE"),
    PAPIRETTERSENDELSE("PAPIRETTERSENDELSE", "Papirettersendelse", "PUNSJ_INNSENDING_TYPE"),
    PAPIRINNTEKTSOPPLYSNINGER("PAPIRINNTEKTSOPPLYSNINGER", "Papirinntektsopplysninger", "PUNSJ_INNSENDING_TYPE"),
    DIGITAL_ETTERSENDELSE("DIGITAL_ETTERSENDELSE", "Digital ettersendelse", "PUNSJ_INNSENDING_TYPE"),
    INNLOGGET_CHAT("INNLOGGET_CHAT", "Innlogget chat", "PUNSJ_INNSENDING_TYPE"),
    SKRIV_TIL_OSS_SPØRMSÅL("SKRIV_TIL_OSS_SPØRMSÅL", "Skriv til oss spørsmål", "PUNSJ_INNSENDING_TYPE"),
    SKRIV_TIL_OSS_SVAR("SKRIV_TIL_OSS_SVAR", "Skriv til oss svar", "PUNSJ_INNSENDING_TYPE"),
    SAMTALEREFERAT("SAMTALEREFERAT", "Samtalereferat", "PUNSJ_INNSENDING_TYPE"),
    KOPI("KOPI", "Kopi", "PUNSJ_INNSENDING_TYPE"),
    INNTEKTSMELDING_UTGÅTT("INNTEKTSMELDING_UTGÅTT", "Inntektsmeldinger uten søknad", "PUNSJ_INNSENDING_TYPE"),
    UTEN_FNR_DNR("UTEN_FNR_DNR", "Uten fnr eller dnr", "PUNSJ_INNSENDING_TYPE"),
    UKJENT("UKJENT", "Ukjent", "PUNSJ_INNSENDING_TYPE");

    companion object {
        @JsonCreator
        @JvmStatic
        fun fraKode(kode: String): BehandlingType = values().find { it.kode == kode } ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
    }
}

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
enum class BehandlingStatus(override val kode: String, override val navn: String) : Kodeverdi {
    AVSLUTTET("AVSLU", "Avsluttet"),
    FATTER_VEDTAK("FVED", "Fatter vedtak"),
    IVERKSETTER_VEDTAK("IVED", "Iverksetter vedtak"),
    OPPRETTET("OPPRE", "Opprettet"),
    UTREDES("UTRED", "Utredes"),

    // de 3 siste gjelder k9-punsj
    SATT_PÅ_VENT("VENT", "Satt på vent"),
    LUKKET("LUKKET", "Lukket"),
    SENDT_INN("SENDT_INN", "Sendt inn");

    override val kodeverk = "BEHANDLING_TYPE"

    companion object {
        @JsonCreator
        @JvmStatic
        fun fraKode(kode: String): BehandlingStatus = values().find { it.kode == kode } ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
    }
}

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
enum class Enhet(val navn: String) {
    NASJONAL("NASJONAL");

    companion object {
        @JsonCreator
        @JvmStatic
        fun fraKode(navn: String): Enhet = values().find { it.navn == navn } ?: throw IllegalStateException("Kjenner ikke igjen navnet=$navn")
    }
}

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
enum class KøSortering(
    override val kode: String,
    override val navn: String,
    val felttype: String,
    val feltkategori: String
) :
    Kodeverdi {
    OPPRETT_BEHANDLING("OPPRBEH", "Dato for opprettelse av behandling", "DATO", ""),
    FORSTE_STONADSDAG("FORSTONAD", "Dato for første stønadsdag", "DATO", "");

    override val kodeverk = "KO_SORTERING"

    companion object {
        @JsonCreator
        @JvmStatic
        fun fraKode(kode: String): KøSortering = values().find { it.kode == kode } ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
    }
}


@JsonFormat(shape = JsonFormat.Shape.OBJECT)
enum class Fagsystem(val kode: String, val kodeverk: String) {
    K9SAK("K9SAK", "FAGSYSTEM"),
    K9TILBAKE("K9TILBAKE", "FAGSYSTEM"),
    FPTILBAKE("FPTILBAKE", "FAGSYSTEM"),
    PUNSJ("PUNSJ", "FAGSYSTEM"),
    OMSORGSPENGER("OMSORGSPENGER", "FAGSYSTEM");

    companion object {
        @JsonCreator
        @JvmStatic
        fun fraKode(kode: String): Fagsystem = values().find { it.kode == kode } ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
    }
}

enum class AksjonspunktStatus(@JsonValue val kode: String, val navn: String) {
    AVBRUTT ("AVBR", "Avbrutt"),
    OPPRETTET("OPPR", "Opprettet"),
    UTFØRT ("UTFO", "Utført");

    companion object {
        private val KODER = values().associateBy { it.kode }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fraKode(kode: String): AksjonspunktStatus {
            return KODER[kode] ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
        }
    }
}