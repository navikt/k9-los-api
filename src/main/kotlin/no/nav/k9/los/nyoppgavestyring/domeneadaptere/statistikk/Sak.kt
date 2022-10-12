package no.nav.k9.los.nyoppgavestyring.domeneadaptere.statistikk

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.k9.statistikk.kontrakter.JsonSchemas
import java.time.LocalDate
import java.time.OffsetDateTime

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Sak(
    @JsonProperty("saksnummer")
    val saksnummer: String,

    @JsonProperty("sakId")
    val sakId: String? = null,

    @JsonProperty("funksjonellTid")
    val funksjonellTid: OffsetDateTime? = null,

    @JsonProperty("tekniskTid")
    val tekniskTid: OffsetDateTime? = null,

    @JsonProperty("opprettetDato")
    val opprettetDato: LocalDate? = null,

    @JsonProperty("aktorId")
    val aktorId: Long? = null,

    @JsonProperty("aktorer")
    val aktorer: List<Aktør> = listOf(),

    @JsonProperty("ytelseType")
    val ytelseType: String? = null,

    @JsonProperty("underType")
    val underType: String? = null,

    @JsonProperty("sakStatus")
    val sakStatus: String? = null,

    @JsonProperty("ytelseTypeBeskrivelse")
    val ytelseTypeBeskrivelse: String? = null,

    @JsonProperty("underTypeBeskrivelse")
    val underTypeBeskrivelse: String? = null,

    @JsonProperty("sakStatusBeskrivelse")
    val sakStatusBeskrivelse: String? = null,

    @JsonProperty("avsender")
    val avsender: String? = null,

    @JsonProperty("versjon")
    val versjon: Long? = null
) {
    companion object {
        fun builder(sakId: String): SakBuilder = SakBuilder(sakId)
        fun fromJson(json: String): Sak =
            JsonSchemas.fromJson(json, Sak::class.java)
    }

    fun toJson(): String = JsonSchemas.toJson(this)
}

// for Java-klienter
class SakBuilder(saksnummer: String) {
    private var sak = Sak(saksnummer = saksnummer)

    fun build(): Sak = sak
    fun buildJson(): String = build().toJson()

    fun funksjonellTid(funksjonellTid: OffsetDateTime?): SakBuilder {
        sak = sak.copy(funksjonellTid = funksjonellTid)
        return this
    }

    fun tekniskTid(tekniskTid: OffsetDateTime?): SakBuilder {
        sak = sak.copy(tekniskTid = tekniskTid)
        return this
    }

    fun opprettetDato(opprettetDato: LocalDate?): SakBuilder {
        sak = sak.copy(opprettetDato = opprettetDato)
        return this
    }

    fun aktorId(aktorId: Long?): SakBuilder {
        sak = sak.copy(aktorId = aktorId)
        return this
    }

    fun aktorer(aktoerer: List<Aktør>): SakBuilder {
        sak = sak.copy(aktorer = aktoerer)
        return this
    }

    fun saksnummer(saksnummer: String): SakBuilder {
        sak = sak.copy(saksnummer = saksnummer)
        return this
    }

    fun ytelsestype(kode: String?, beskrivelse: String?): SakBuilder {
        sak = sak.copy(ytelseType = kode, ytelseTypeBeskrivelse = beskrivelse)
        return this
    }

    fun undertype(kode: String?, beskrivelse: String?): SakBuilder {
        sak = sak.copy(underType = kode, underTypeBeskrivelse = beskrivelse)
        return this
    }

    fun sakStatus(kode: String?, beskrivelse: String?): SakBuilder {
        sak = sak.copy(sakStatus = kode, sakStatusBeskrivelse = beskrivelse)
        return this
    }

    fun avsender(avsender: String?): SakBuilder {
        sak = sak.copy(avsender = avsender)
        return this
    }

    fun versjon(versjon: Long?): SakBuilder {
        sak = sak.copy(versjon = versjon)
        return this
    }

    fun sakId(sakId: String?): SakBuilder {
        sak = sak.copy(sakId = sakId)
        return this
    }

}