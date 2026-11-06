package no.nav.k9.los.domeneadaptere.statistikk.k9

import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.k9.statistikk.kontrakter.JsonSchemas
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Behandling(
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
    val beslutter: String? = null,
    val saksbehandler: String? = null,
    val behandlingOpprettetAv: String? = null,
    val behandlingOpprettetType: String? = null,
    val behandlingOpprettetTypeBeskrivelse: String? = null,
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
        fun fromJson(json: String): Behandling =
            JsonSchemas.fromJson(json, Behandling::class.java)

        fun builder(behandlingId: String, saksnummer: String): BehandlingBuilder = BehandlingBuilder(behandlingId, saksnummer)
    }

    fun toJson(): String = JsonSchemas.toJson(this)
}

class BehandlingBuilder(behandlingId: String, saksnummer: String) {
    private var behandling = Behandling(
        behandlingId = behandlingId,
        saksnummer = saksnummer,
    )

    fun build(): Behandling = behandling

    fun behandlingId(behandlingId: String): BehandlingBuilder {
        behandling = behandling.copy(behandlingId = behandlingId)
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

    fun saksnummer(saksnummer: String): BehandlingBuilder {
        behandling = behandling.copy(saksnummer = saksnummer)
        return this
    }

    fun behandlingType(kode: String?, beskrivelse: String?): BehandlingBuilder {
        behandling = behandling.copy(behandlingType = kode, behandlingTypeBeskrivelse = beskrivelse)
        return this
    }
}
