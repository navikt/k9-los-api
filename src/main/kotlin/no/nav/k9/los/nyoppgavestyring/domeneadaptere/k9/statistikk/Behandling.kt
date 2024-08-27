package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.k9.statistikk.kontrakter.JsonSchemas
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Behandling(
    @JsonProperty("sakId")
    val sakId: String? = null,

    @JsonProperty("behandlingId")
    val behandlingId: String,

    @JsonProperty("funksjonellTid")
    val funksjonellTid: OffsetDateTime? = null,

    @JsonProperty("tekniskTid")
    val tekniskTid: OffsetDateTime? = null,

    @JsonProperty("mottattDato")
    val mottattDato: LocalDate? = null,

    @JsonProperty("registrertDato")
    val registrertDato: LocalDate? = null,

    @JsonProperty("vedtaksDato")
    val vedtaksDato: LocalDate? = null,

    @JsonProperty("relatertBehandlingId")
    val relatertBehandlingId: String? = null,

    @JsonProperty("vedtakId")
    val vedtakId: String? = null,

    @JsonProperty("saksnummer")
    val saksnummer: String? = null,

    @JsonProperty("behandlingType")
    val behandlingType: String? = null,

    @JsonProperty("behandlingStatus")
    val behandlingStatus: String? = null,

    @JsonProperty("resultat")
    val resultat: String? = null,

    @JsonProperty("resultatBegrunnelse")
    val resultatBegrunnelse: String? = null,

    @JsonProperty("utenlandstilsnitt")
    val utenlandstilsnitt: Boolean? = null,

    @JsonProperty("behandlingTypeBeskrivelse")
    val behandlingTypeBeskrivelse: String? = null,

    @JsonProperty("behandlingStatusBeskrivelse")
    val behandlingStatusBeskrivelse: String? = null,

    @JsonProperty("resultatBeskrivelse")
    val resultatBeskrivelse: String? = null,

    @JsonProperty("resultatBegrunnelseBeskrivelse")
    val resultatBegrunnelseBeskrivelse: String? = null,

    @JsonProperty("utenlandstilsnittBeskrivelse")
    val utenlandstilsnittBeskrivelse: String? = null,

    @JsonProperty("beslutter")
    val beslutter: String? = null,

    @JsonProperty("saksbehandler")
    val saksbehandler: String? = null,

    @JsonProperty("behandlingOpprettetAv")
    val behandlingOpprettetAv: String? = null,

    @JsonProperty("behandlingOpprettetType")
    val behandlingOpprettetType: String? = null,

    @JsonProperty("behandlingOpprettetTypeBeskrivelse")
    val behandlingOpprettetTypeBeskrivelse: String? = null,

    @JsonProperty("ansvarligEnhetKode")
    val ansvarligEnhetKode: String? = null,

    @JsonProperty("ansvarligEnhetType")
    val ansvarligEnhetType: String? = null,

    @JsonProperty("datoForUttak")
    val datoForUttak: LocalDate? = null,

    @JsonProperty("datoForUtbetaling")
    val datoForUtbetaling: LocalDate? = null,

    @JsonProperty("totrinnsbehandling")
    val totrinnsbehandling: Boolean? = null,

    @JsonProperty("helautomatiskBehandlet")
    val helautomatiskBehandlet: Boolean? = false,

    @JsonProperty("avsender")
    val avsender: String? = null,

    @JsonProperty("oversendtKabal")
    val oversendtKlageinstans: LocalDateTime? = null,

    @JsonProperty("versjon")
    val versjon: Long? = null
) {
    companion object {
        fun fromJson(json: String): Behandling =
            JsonSchemas.fromJson(json, Behandling::class.java)

        fun builder(behandlingId: String, saksnummer: String): BehandlingBuilder = BehandlingBuilder(behandlingId, saksnummer)
    }

    fun toJson(): String = JsonSchemas.toJson(this)

    fun tryggToString(): String {
        return """Behandling(
            saksnummer=$saksnummer,
            behandlingId=$behandlingId,
            funksjonellTid=$funksjonellTid,
            tekniskTid=$tekniskTid,
            registrertDato=$registrertDato,
            vedtaksDato=$vedtaksDato,
            avsender=$avsender,
            oversendtKlageinstans=$oversendtKlageinstans
        )""".trimIndent()
    }

}

class BehandlingBuilder(behandlingId: String, saksnummer: String) {
    private var behandling = Behandling(
        behandlingId = behandlingId,
        saksnummer = saksnummer,
    )

    fun build(): Behandling = behandling
    fun buildJson(): String = build().toJson()

    fun sakId(sakId: String?): BehandlingBuilder {
        behandling = behandling.copy(sakId = sakId)
        return this
    }

    fun behandlingId(behandlingId: String): BehandlingBuilder {
        behandling = behandling.copy(behandlingId = behandlingId)
        return this
    }

    fun funksjonellTid(funksjonellTid: OffsetDateTime?): BehandlingBuilder {
        behandling = behandling.copy(funksjonellTid = funksjonellTid)
        return this
    }

    fun tekniskTid(tekniskTid: OffsetDateTime?): BehandlingBuilder {
        behandling = behandling.copy(tekniskTid = tekniskTid)
        return this
    }

    fun mottattDato(mottattDato: LocalDate?): BehandlingBuilder {
        behandling = behandling.copy(mottattDato = mottattDato)
        return this
    }

    fun registrertDato(registrertDato: LocalDate?): BehandlingBuilder {
        behandling = behandling.copy(registrertDato = registrertDato)
        return this
    }

    fun vedtaksDato(vedtaksDato: LocalDate?): BehandlingBuilder {
        behandling = behandling.copy(vedtaksDato = vedtaksDato)
        return this
    }

    fun relatertBehandlingId(relatertBehandlingId: String?): BehandlingBuilder {
        behandling = behandling.copy(relatertBehandlingId = relatertBehandlingId)
        return this
    }

    fun vedtakId(vedtakId: String?): BehandlingBuilder {
        behandling = behandling.copy(vedtakId = vedtakId)
        return this
    }

    fun saksnummer(saksnummer: String): BehandlingBuilder {
        behandling = behandling.copy(saksnummer = saksnummer)
        return this
    }

    fun behandlingType(kode: String?, beskrivelse: String?): BehandlingBuilder {
        behandling = behandling.copy(behandlingType = kode, behandlingTypeBeskrivelse = beskrivelse)
        return this
    }
}
