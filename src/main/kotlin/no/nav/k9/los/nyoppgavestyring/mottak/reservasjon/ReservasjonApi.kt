package no.nav.k9.los.nyoppgavestyring.mottak.reservasjon

import io.ktor.server.routing.*
import no.nav.k9.los.Configuration
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeTjeneste
import org.koin.ktor.ext.inject

internal fun Route.ReservasjonApi() {
    val requestContextService by inject<RequestContextService>()
    val oppgavetypeTjeneste by inject<OppgavetypeTjeneste>()
    val config by inject<Configuration>()


}