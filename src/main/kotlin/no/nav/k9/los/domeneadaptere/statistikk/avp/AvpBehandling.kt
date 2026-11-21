package no.nav.k9.los.domeneadaptere.statistikk.avp

import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.k9.los.domeneadaptere.statistikk.Aktør
import no.nav.k9.los.infrastruktur.utils.SensitiveField
import no.nav.k9.statistikk.kontrakter.JsonSchemas
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AvpBehandling(
    val sakId: String? = null,
    val behandlingId: String,
    @field:SensitiveField
    val søkersAktørId: Aktør,
    val funksjonellTid: OffsetDateTime? = null,
    val tekniskTid: OffsetDateTime? = null,
    val mottattDato: LocalDate? = null,
    val registrertDato: LocalDate? = null,
    val vedtaksDato: LocalDate? = null,
    val saksnummer: String? = null,
    val behandlingType: String? = null,
    val behandlingTypeBeskrivelse: String? = null,
    val behandlingStatus: String? = null,
    val behandlingStatusBeskrivelse: String? = null,
    val resultat: String? = null,
    val resultatBeskrivelse: String? = null,
    val vedtakId: String? = null,
    val utenlandssak: Boolean? = null,
    @field:SensitiveField
    val beslutter: String? = null,
    @field:SensitiveField
    val saksbehandler: String? = null,
    @field:SensitiveField("2103")
    val ansvarligEnhetKode: String,
    val totrinnsbehandling: Boolean? = null,
    val helautomatiskBehandlet: Boolean? = false,
    val avsender: String? = null,

    //for klager:
    val relatertBehandlingId: String? = null,
    val relatertBehandlingFagsystem: String? = null, //vil alltid være ung-sak
    val oversendtKlageinstans: LocalDateTime? = null,
) {
    companion object {
        fun fromJson(json: String): AvpBehandling =
            JsonSchemas.fromJson(json, AvpBehandling::class.java)
    }

    fun toJson(): String = JsonSchemas.toJson(this)
}
