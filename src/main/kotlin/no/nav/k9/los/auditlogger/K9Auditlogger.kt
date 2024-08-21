package no.nav.k9.los.auditlogger

import no.nav.k9.los.integrasjon.abac.Action
import no.nav.k9.los.integrasjon.abac.Auditlogging
import no.nav.k9.los.integrasjon.abac.TILGANG_SAK
import no.nav.k9.los.integrasjon.audit.*
import java.time.LocalDateTime
import java.time.ZoneOffset

class K9Auditlogger(
    private val auditlogger: Auditlogger
) {
    fun betingetLogging(tilgang: Boolean, auditlogging: Auditlogging, callback: () -> Unit) {
        if (auditlogging == Auditlogging.ALLTID_LOGG || (tilgang && (auditlogging == Auditlogging.LOGG_VED_PERMIT))) {
            callback()
        }
    }

    fun loggTilgangK9Sak(fagsakNummer: String, aktorId: String, identTilInnloggetBruker: String, action: Action) {
        auditlogger.logg(
            Auditdata(
                header = AuditdataHeader(
                    vendor = auditlogger.defaultVendor,
                    product = auditlogger.defaultProduct,
                    eventClassId = EventClassId.AUDIT_SEARCH,
                    name = "ABAC Sporingslogg",
                    severity = "INFO"
                ), fields = setOf(
                    CefField(CefFieldName.EVENT_TIME, LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) * 1000L),
                    CefField(CefFieldName.REQUEST, "read"),
                    CefField(CefFieldName.ABAC_RESOURCE_TYPE, TILGANG_SAK),
                    CefField(CefFieldName.ABAC_ACTION, action.name),
                    CefField(CefFieldName.USER_ID, identTilInnloggetBruker),
                    CefField(CefFieldName.BERORT_BRUKER_ID, aktorId),

                    CefField(CefFieldName.BEHANDLING_VERDI, "behandlingsid"),
                    CefField(CefFieldName.BEHANDLING_LABEL, "Behandling"),
                    CefField(CefFieldName.SAKSNUMMER_VERDI, fagsakNummer),
                    CefField(CefFieldName.SAKSNUMMER_LABEL, "Saksnummer")
                )
            )
        )
    }

    fun loggTilgangK9Punsj(aktorId: String, identTilInnloggetBruker: String, action: Action) {
        // Loggingen her må gås opp, usikkert om dette er tilstrekkelig
        auditlogger.logg(
            Auditdata(
                header = AuditdataHeader(
                    vendor = auditlogger.defaultVendor,
                    product = auditlogger.defaultProduct,
                    eventClassId = EventClassId.AUDIT_SEARCH,
                    name = "ABAC Sporingslogg",
                    severity = "INFO"
                ), fields = setOf(
                    CefField(CefFieldName.EVENT_TIME, LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) * 1000L),
                    CefField(CefFieldName.REQUEST, "read"),
                    CefField(CefFieldName.ABAC_RESOURCE_TYPE, TILGANG_SAK),
                    CefField(CefFieldName.ABAC_ACTION, action.name),
                    CefField(CefFieldName.USER_ID, identTilInnloggetBruker),
                    CefField(CefFieldName.BERORT_BRUKER_ID, aktorId),
                )
            )
        )
    }
}