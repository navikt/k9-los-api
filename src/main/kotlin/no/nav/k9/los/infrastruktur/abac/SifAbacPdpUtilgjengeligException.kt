package no.nav.k9.los.infrastruktur.abac

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.response.respond

internal class SifAbacPdpUtilgjengeligException(cause: Throwable? = null) :
    IllegalStateException("Tidsavbrudd mot sif-abac-pdp", cause)

internal fun StatusPagesConfig.sifAbacPdpStatusPages() {
    exception<SifAbacPdpUtilgjengeligException> { call, _ ->
        call.respond(HttpStatusCode.ServiceUnavailable)
    }
}
