package no.nav.k9.los.domeneadaptere.statistikk.k9

import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.k9.statistikk.kontrakter.JsonSchemas
import no.nav.k9.los.infrastruktur.utils.SensitiveField
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

@JsonInclude(JsonInclude.Include.NON_NULL)
data class K9Behandling(
    val sakId: String? = null,
    val behandlingId: String,
    val funksjonellTid: OffsetDateTime? = null,
    val tekniskTid: OffsetDateTime? = null,
    val mottattDato: LocalDate? = null,
    val registrertDato: LocalDate? = null,
    val vedtaksDato: LocalDate? = null,
    val relatertBehandlingId: String? = null,
    val relatertBehandlingFagsystem: String? = null,
    val vedtakId: String? = null,
    val saksnummer: String? = null,
    val behandlingType: String? = null,
    val behandlingStatus: String? = null,
    val resultat: String? = null,
    val resultatBegrunnelse: String? = null,
    val utenlandstilsnitt: Boolean? = null,
    val behandlingTypeBeskrivelse: String? = null,
    val behandlingStatusBeskrivelse: String? = null,
    val resultatBeskrivelse: String? = null,
    val resultatBegrunnelseBeskrivelse: String? = null,
    val utenlandstilsnittBeskrivelse: String? = null,
    @field:SensitiveField
    val beslutter: String? = null,
    @field:SensitiveField
    val saksbehandler: String? = null,
    @field:SensitiveField
    val behandlingOpprettetAv: String? = null,
    val behandlingOpprettetType: String? = null,
    val behandlingOpprettetTypeBeskrivelse: String? = null,
    @field:SensitiveField
    val ansvarligEnhetKode: String? = null,
    val ansvarligEnhetType: String? = null,
    val datoForUttak: LocalDate? = null,
    val datoForUtbetaling: LocalDate? = null,
    val totrinnsbehandling: Boolean? = null,
    val helautomatiskBehandlet: Boolean? = false,
    val avsender: String? = null,
    val oversendtKlageinstans: LocalDateTime? = null,
    val versjon: Long? = null
) {
    companion object {
        fun fromJson(json: String): K9Behandling =
            JsonSchemas.fromJson(json, K9Behandling::class.java)

        fun builder(behandlingId: String, saksnummer: String): BehandlingBuilder = BehandlingBuilder(behandlingId, saksnummer)
    }

    fun toJson(): String = JsonSchemas.toJson(this)
}

class BehandlingBuilder(behandlingId: String, saksnummer: String) {
    private var k9Behandling = K9Behandling(
        behandlingId = behandlingId,
        saksnummer = saksnummer,
    )

    fun build(): K9Behandling = k9Behandling

    fun behandlingId(behandlingId: String): BehandlingBuilder {
        k9Behandling = k9Behandling.copy(behandlingId = behandlingId)
        return this
    }

    fun mottattDato(mottattDato: LocalDate?): BehandlingBuilder {
        k9Behandling = k9Behandling.copy(mottattDato = mottattDato)
        return this
    }

    fun registrertDato(registrertDato: LocalDate?): BehandlingBuilder {
        k9Behandling = k9Behandling.copy(registrertDato = registrertDato)
        return this
    }

    fun saksnummer(saksnummer: String): BehandlingBuilder {
        k9Behandling = k9Behandling.copy(saksnummer = saksnummer)
        return this
    }

    fun behandlingType(kode: String?, beskrivelse: String?): BehandlingBuilder {
        k9Behandling = k9Behandling.copy(behandlingType = kode, behandlingTypeBeskrivelse = beskrivelse)
        return this
    }
}
