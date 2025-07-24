package no.nav.k9.los.nyoppgavestyring.infrastruktur.audit

import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.Action
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.Auditlogging
import java.time.LocalDateTime
import java.time.ZoneOffset

class K9Auditlogger(
    private val auditlogger: Auditlogger
) {
    companion object {
        const val TILGANG_SAK = "no.nav.abac.attributter.k9.fagsak"
    }

    fun betingetLogging(tilgang: Boolean, auditlogging: Auditlogging, callback: K9Auditlogger.() -> Unit) {
        if (auditlogging == Auditlogging.ALLTID_LOGG || (tilgang && (auditlogging == Auditlogging.LOGG_VED_PERMIT))) {
            callback(this)
        }
    }

    fun loggTilgangK9Sak(
        fagsakNummer: String,
        aktorId: String,
        identTilInnloggetBruker: String,
        action: Action,
        tilgang: Boolean
    ) {
        auditlogger.logg(
            Auditdata(
                header = AuditdataHeader(
                    vendor = auditlogger.defaultVendor,
                    product = auditlogger.defaultProduct,
                    eventClassId = EventClassId.AUDIT_SEARCH,
                    name = "ABAC Sporingslogg",
                    severity = if (tilgang) "INFO" else "WARN"
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

    fun loggTilgangK9Punsj(aktorId: String, identTilInnloggetBruker: String, action: Action, tilgang: Boolean) {
        // Loggingen her må gås opp, usikkert om dette er tilstrekkelig
        auditlogger.logg(
            Auditdata(
                header = AuditdataHeader(
                    vendor = auditlogger.defaultVendor,
                    product = auditlogger.defaultProduct,
                    eventClassId = EventClassId.AUDIT_SEARCH,
                    name = "ABAC Sporingslogg",
                    severity = if (tilgang) "INFO" else "WARN"
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