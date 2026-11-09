package no.nav.k9.los.domeneadaptere.statistikk.ung

import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.k9.statistikk.kontrakter.JsonSchemas
import no.nav.ung.sak.typer.AktørId
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

@JsonInclude(JsonInclude.Include.NON_NULL)
data class UngBehandling(
    val sakId: String? = null,
    val behandlingId: String,
    @field:no.nav.k9.los.forvaltning.SensitiveField
    val søkersAktørId: AktørId,
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
    val beslutter: String? = null,
    val saksbehandler: String? = null,
    val ansvarligEnhetKode: String,
    val totrinnsbehandling: Boolean? = null,
    val helautomatiskBehandlet: Boolean? = false,
    val avsender: String? = null,

    //for klager:
    val relatertBehandlingId: String? = null,
    val relatertBehandlingFagsystem: String? = null,
    val oversendtKlageinstans: LocalDateTime? = null,
) {
    companion object {
        fun fromJson(json: String): UngBehandling =
            JsonSchemas.fromJson(json, UngBehandling::class.java)
    }

    fun toJson(): String = JsonSchemas.toJson(this)
}