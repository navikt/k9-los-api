package no.nav.k9.los.nyoppgavestyring.kodeverk

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.TextNode


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
    SØKNAD_OM_NYE_PERIODER("BT-011", "Søknad om nye perioder", "N/A"),

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

enum class BehandlendeEnhet(override val kode: String, override val navn: String, override val kodeverk: String): Kodeverdi {
    STYRINGSENHET("4400", "NAV ARBEID OG YTELSER STYRINGSENHET", "BEHANDLENDE_ENHET"),
    KRISTIANIA("4403", "NAV ARBEID OG YTELSER KRISTIANIA", "BEHANDLENDE_ENHET"),
    SØRLANDET("4410", "NAV ARBEID OG YTELSER SØRLANDET", "BEHANDLENDE_ENHET"),
    YTELSESAVDELINGEN("2830", "YTELSESAVDELINGEN", "BEHANDLENDE_ENHET"),
    UKJENT("UKJENT", "Ukjent", "BEHANDLENDE_ENHET");

    companion object {
        fun fraKode(o: Any): BehandlendeEnhet {
            return entries.find { it.kode == o } ?: UKJENT
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
