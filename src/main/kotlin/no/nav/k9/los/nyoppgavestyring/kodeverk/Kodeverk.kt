package no.nav.k9.los.nyoppgavestyring.kodeverk

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.TextNode
import no.nav.k9.los.domene.lager.oppgave.Kodeverdi
import no.nav.k9.los.domene.modell.KøKritererTypeValidatorer.FlaggValidator
import no.nav.k9.los.domene.modell.KøKritererTypeValidatorer.HeltallRangeValidator
import no.nav.k9.los.domene.modell.KøKritererTypeValidatorer.KodeverkValidator
import no.nav.k9.los.domene.modell.KøKriterierTypeValidator


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
    FORLENGELSER_FRA_INFOTRYGD_AKSJONSPUNKT(
        "FORLENGELSER_FRA_INFOTRYGD_AKSJONSPUNKT",
        "Forlengelser fra infotrygd aksjonspunkt"
    ),
    AARSKVANTUM("AARSKVANTUM", "Årskvantum"),
    IKKE_JOURNALFØRT("IKKE_JOURNALFØRT", "Ikke journalført")
    ;

    override val kodeverk = "ANDRE_KRITERIER_TYPE"

    companion object {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fraKode(o: Any): AndreKriterierType {
            val kode = TempAvledeKode.getVerdi(o)
            return values().find { it.kode == kode } ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
        }
    }
}

// Tanken er på sikt å erstatte AndreKriterierType og SorteringDatoDto og evt. andre filtereringsmuligheter med denne
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@JsonInclude(value = JsonInclude.Include.NON_ABSENT, content = JsonInclude.Include.NON_EMPTY)
enum class KøKriterierType(
    override val kode: String,
    override val navn: String,
    val felttype: KøKriterierFeltType,
    val felttypeKodeverk: String? = null,
    @JsonIgnore val validator: KøKriterierTypeValidator
) : Kodeverdi {
    FEILUTBETALING(
        kode = "FEILUTBETALING",
        navn = "Feilutbetalt beløp",
        felttype = KøKriterierFeltType.BELØP,
        validator = HeltallRangeValidator
    ),
    BEHANDLINGTYPE(
        kode = "BEHANDLINGTYPE",
        navn = "Behandling type",
        felttype = KøKriterierFeltType.KODEVERK,
        felttypeKodeverk = BehandlingType::class.java.simpleName,
        validator = KodeverkValidator { BehandlingType.fraKode(it) }
    ),
    OPPGAVEKODE(
        kode = "OPPGAVEKODE",
        navn = "Oppgavekode",
        felttype = KøKriterierFeltType.KODEVERK,
        felttypeKodeverk = OppgaveKode::class.java.simpleName,
        validator = KodeverkValidator { OppgaveKode.fraKode(it) }
    ),
    MERKNADTYPE(
        kode = "MERKNADTYPE",
        navn = "Merknad type",
        felttype = KøKriterierFeltType.KODEVERK,
        felttypeKodeverk = MerknadType::class.java.simpleName,
        validator = KodeverkValidator { MerknadType.fraKode(it) }
    ),
    NYE_KRAV(
        kode = "NYE_KRAV",
        navn = "Nye søknadsperioder",
        felttype = KøKriterierFeltType.FLAGG,
        validator = FlaggValidator
    ),
    FRA_ENDRINGSDIALOG(
        kode = "FRA_ENDRINGSDIALOG",
        navn = "Har endring fra endringsdialog",
        felttype = KøKriterierFeltType.FLAGG,
        validator = FlaggValidator
    ),
    ;


    companion object {
        private val KODER = values().associateBy { it.kode }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fraKode(kode: String): KøKriterierType {
            return KODER[kode] ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
        }
    }

    override val kodeverk = "KØ_KRITERIER_TYPE"

}

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
enum class KøKriterierFeltType(@JsonValue val kode: String) {
    BELØP("BELOP"), KODEVERK("KODEVERK"), FLAGG("FLAGG")
}

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
enum class FagsakYtelseType constructor(override val kode: String, override val navn: String) : Kodeverdi {
    PLEIEPENGER_SYKT_BARN("PSB", "Pleiepenger sykt barn"),
    OMSORGSPENGER("OMP", "Omsorgspenger"),
    OMSORGSDAGER("OMD", "Omsorgsdager: overføring"),
    FRISINN("FRISINN", "Frisinn"),
    PPN("PPN", "Pleiepenger i livets sluttfase"),
    OLP("OLP", "Opplæringspenger"),
    OMSORGSPENGER_KS("OMP_KS", "Omsorgsdager: kronisk syk"),
    OMSORGSPENGER_MA("OMP_MA", "Omsorgsdager: midlertidig alene"),
    OMSORGSPENGER_AO("OMP_AO", "Omsorgsdager: alene om omsorg"),
    UNGDOMSYTELSE("UNG", "Ungdomsytelse"),
    UKJENT("UKJENT", "Ukjent");

    override val kodeverk = "FAGSAK_YTELSE_TYPE"

    companion object {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fraKode(o: Any): FagsakYtelseType {
            val kode = TempAvledeKode.getVerdi(o)
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
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fraKode(o: Any): FagsakStatus {
            val kode = TempAvledeKode.getVerdi(o)
            return values().find { it.kode == kode } ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
        }
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
    DIGITAL_SØKNAD("DIGITAL_SØKNAD", "Digital søknad", "PUNSJ_INNSENDING_TYPE"),
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
    PUNSJOPPGAVE_IKKE_LENGER_NØDVENDIG("PUNSJOPPGAVE_IKKE_LENGER_NØDVENDIG", "Punsjoppgave ikke lenger nødvendig", "PUNSJ_INNSENDING_TYPE"), // Prodfiks: Lagt til fordi den er mottatt på kafka
    JOURNALPOSTNOTAT("JOURNALPOSTNOTAT", "Manuelt opprettet journalpostnotat", "PUNSJ_INNSENDING_TYPE"),
    UKJENT("UKJENT", "Ukjent", "PUNSJ_INNSENDING_TYPE");

    companion object {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fraKode(o: Any): BehandlingType {
            val kode = TempAvledeKode.getVerdi(o)
            return entries.find { it.kode == kode } ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
        }
    }

    fun gjelderPunsj() : Boolean {
        return kodeverk == "PUNSJ_INNSENDING_TYPE"
    }
}

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
enum class OppgaveKode(override val kode: String, override val navn: String, val gruppering: String) : Kodeverdi {
    // Innledene behandling
    MEDLEMSKAP("5053", "Medlemskap", OppgaveKodeGruppe.INNLEDENDE_BEHANDLING.navn),
    SØKNADSFRIST("5077", "Søknadsfrist", OppgaveKodeGruppe.INNLEDENDE_BEHANDLING.navn),
    OPPTJENING("5089", "Opptjening", OppgaveKodeGruppe.INNLEDENDE_BEHANDLING.navn),
    SYKDOM("9001", "Sykdom", OppgaveKodeGruppe.INNLEDENDE_BEHANDLING.navn),
    OMSORGEN_FOR("9020", "Omsorgen for", OppgaveKodeGruppe.INNLEDENDE_BEHANDLING.navn),
    AVKLAR_VERGE("5030", "Avklar verge", OppgaveKodeGruppe.INNLEDENDE_BEHANDLING.navn),
    UTENLANDSINNTEKT("5068", "Automatisk markering av utenlandssak", OppgaveKodeGruppe.INNLEDENDE_BEHANDLING.navn),

    // Om barnet
    NATTEVÅK("9200", "Nattevåk", OppgaveKodeGruppe.OM_BARNET.navn),
    BEREDSKAP("9201", "Beredskap", OppgaveKodeGruppe.OM_BARNET.navn),
    BARNS_DØD("9202", "Barns død", OppgaveKodeGruppe.OM_BARNET.navn),

    // Mangler inntektsmelding
    AVKLAR_MANGLENDE_IM("9069", "Avklar manglende IM", OppgaveKodeGruppe.MANGLER_INNTEKTSMELDING.navn),
    ENDELIG_AVKLARING_MANGLER_IM(
        "9071",
        "Endelig avklaring mangler IM",
        OppgaveKodeGruppe.MANGLER_INNTEKTSMELDING.navn
    ),
    MANGLER_ARBEIDSTID("9203", "Mangler arbeidstid", OppgaveKodeGruppe.MANGLER_INNTEKTSMELDING.navn),

    // Beregning
    FASTSETT_BEREGNINGSGRUNNLAG("5038", "Fastsett beregningsgrunnlag", OppgaveKodeGruppe.BEREGNING.navn),
    NY_ENDRET_SN_VARIG_ENDRING("5039", "Ny/endret SN (varig endring)", OppgaveKodeGruppe.BEREGNING.navn),
    NY_ENDRET_SN_NY_I_ARB_LIVET("5049", "Ny/endret SN (ny i arb.livet)", OppgaveKodeGruppe.BEREGNING.navn),
    FORDEL_BEREGNINGSGRUNNLAG("5046", "Fordel beregningsgrunnlag", OppgaveKodeGruppe.BEREGNING.navn),
    TIDSBEGRENSET_ARBEIDSFORHOLD("5047", "Tidsbegrenset arbeidsforhold", OppgaveKodeGruppe.BEREGNING.navn),
    AKTIVITETER("5052", "Aktiviteter", OppgaveKodeGruppe.BEREGNING.navn),
    BEREGNINGSFAKTA("5058", "Beregningsfakta", OppgaveKodeGruppe.BEREGNING.navn),
    FEILUTBETALING("5084", "Feilutbetaling", OppgaveKodeGruppe.BEREGNING.navn),
    OVERSTYRING_BEREGNINGSAKTIVITET("6014", "Overstyring beregningsaktivitet", OppgaveKodeGruppe.BEREGNING.navn),
    OVERSTYRING_BEREGNINGSGRUNNLAG("6015", "Overstyring beregningsgrunnlag", OppgaveKodeGruppe.BEREGNING.navn),

    // Flyttesaker
    MANUELL_BEREGNING("9005", "Manuell beregning", OppgaveKodeGruppe.FLYTTESAKER.navn),
    INFOTRYGDSØKNAD("9007", "Infotrygsøknad", OppgaveKodeGruppe.FLYTTESAKER.navn),
    INFOTRYGDSØKNAD_TO_PERSONER("9008", "Infotrygdsøknad 2 personer", OppgaveKodeGruppe.FLYTTESAKER.navn),

    // Fatte vedtak
    FORESLÅ_VEDTAK("5015", "Foreslå vedtak", OppgaveKodeGruppe.FATTE_VEDTAK.navn),
    FORESLÅ_VEDTAK_MANUELT("5028", "Foreslå vedtak manuelt", OppgaveKodeGruppe.FATTE_VEDTAK.navn),
    VURDERE_ANNEN_YTELSE_FØR_VEDTAK_KODE("5033", "Sjekk VKY", OppgaveKodeGruppe.FATTE_VEDTAK.navn),
    VURDER_DOKUMENT("5034", "Vurder dokument", OppgaveKodeGruppe.FATTE_VEDTAK.navn),

    // Uttak
    VURDER_DATO_NY_REGEL_UTTAK("9291", "Ny inntekt", OppgaveKodeGruppe.UTTAK.navn),
    VURDER_OVERLAPPENDE_SØSKENSAK("9292", "Vurder overlappende søskensaker", OppgaveKodeGruppe.UTTAK.navn),
    VURDER_NYOPPSTARTET("9016", "Vurder nyoppstartet", OppgaveKodeGruppe.UTTAK.navn),

    // Uspesifisert
    KONTROLL_MANUELL_REVURDERING("5056", "Kontroll manuell revurdering", OppgaveKodeGruppe.USPESIFISERT.navn),
    VURDER_REFUSJON_BERGRUNN_KODE("5059", "Mangler navn", OppgaveKodeGruppe.USPESIFISERT.navn);

    override val kodeverk = "OPPGAVE_KODE"

    companion object {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fraKode(o: Any): OppgaveKode {
            val kode = TempAvledeKode.getVerdi(o)
            return entries.find { it.kode == kode }
                ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
        }
    }
}

enum class OppgaveKodeGruppe(val navn: String) {
    INNLEDENDE_BEHANDLING("Innledende behandling"),
    OM_BARNET("Om barnet"),
    MANGLER_INNTEKTSMELDING("Mangler inntektsmelding"),
    BEREGNING("Beregning"),
    FLYTTESAKER("Flyttesaker"),
    FATTE_VEDTAK("Fatte vedtak"),
    USPESIFISERT("Uspesifisert"),
    UTTAK("Uttak")
    ;
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
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fraKode(o: Any): BehandlingStatus {
            val kode = TempAvledeKode.getVerdi(o)
            return values().find { it.kode == kode } ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
        }
    }
}

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
enum class Enhet(val navn: String) {
    NASJONAL("NASJONAL");

    companion object {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fraKode(o: Any): Enhet {
            val navn = TempAvledeKode.getVerdi(o, "navn")
            return values().find { it.navn == navn } ?: throw IllegalStateException("Kjenner ikke igjen navnet=$navn")
        }
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
    FORSTE_STONADSDAG("FORSTONAD", "Dato for første stønadsdag", "DATO", ""),
    FEILUTBETALT("FEILUTBETALT", "Sorter etter høyeste feilutbetalt beløp", "BELOP", "");

    override val kodeverk = "KO_SORTERING"

    companion object {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fraKode(o: Any): KøSortering {
            val kode = TempAvledeKode.getVerdi(o)
            return values().find { it.kode == kode } ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
        }
    }
}


@JsonFormat(shape = JsonFormat.Shape.OBJECT)
enum class Fagsystem(override val kode: String, override val kodeverk: String, override val navn: String): Kodeverdi {
    K9SAK("K9SAK", "FAGSYSTEM", "K9-sak"),
    K9TILBAKE("K9TILBAKE", "FAGSYSTEM", "K9-tilbake"),
    K9KLAGE("K9KLAGE", "FAGSYSTEM", "K9-klage"),
    PUNSJ("PUNSJ", "FAGSYSTEM", "K9-punsj");

    companion object {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fraKode(o: Any): Fagsystem {
            val kode = TempAvledeKode.getVerdi(o)
            return values().find { it.kode == kode } ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
        }
    }
}

enum class AksjonspunktStatus(@JsonValue val kode: String, val navn: String) {
    AVBRUTT("AVBR", "Avbrutt"),
    OPPRETTET("OPPR", "Opprettet"),
    UTFØRT("UTFO", "Utført");

    companion object {
        private val KODER = values().associateBy { it.kode }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fraKode(kode: String): AksjonspunktStatus {
            return KODER[kode] ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
        }
    }
}

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
enum class MerknadType(override val kode: String, override val navn: String) : Kodeverdi {
    HASTESAK("HASTESAK", "Hastesak"),
    VANSKELIG("VANSKELIG", "Vanskelig sak");

    override val kodeverk = "MERKNADTYPE"

    companion object {
        private val KODER = values().associateBy { it.kode }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fraKode(kode: String): MerknadType {
            return KODER[kode] ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
        }
    }
}


/**
 * for avledning av kode for enum som ikke er mappet direkte på navn der både ny (@JsonValue) og gammel (@JsonProperty kode + kodeverk) kan
 * bli sendt. Brukes til eksisterende kode er konvertert til @JsonValue på alle grensesnitt.
 *
 * <h3>Eksempel - [BehandlingType]</h3>
 * **Gammel**: {"kode":"BT-004","kodeverk":"BEHANDLING_TYPE"}
 *
 *
 * **Ny**: "BT-004"
 *
 *
 *
 */
@Deprecated("endre grensesnitt til @JsonValue istdf @JsonProperty + @JsonCreator")
private object TempAvledeKode {
    fun getVerdi(node: Any, key: String = "kode"): String? {
        return when (node) {
            is String -> node
            is TextNode -> node.asText()
            is JsonNode -> node[key].asText()
            is Map<*, *> -> node[key] as String?
            else -> throw IllegalArgumentException("Støtter ikke node av type: " + node.javaClass)
        }
    }
}
