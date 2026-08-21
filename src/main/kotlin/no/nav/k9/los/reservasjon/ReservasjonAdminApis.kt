package no.nav.k9.los.reservasjon

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.brukerkontekst.medBrukerkontekst
import org.koin.ktor.ext.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("nav.OppgaveApis")

//TODO generell sikring kode6 - se etter feil
//TODO fjern reservasjonsid fra objekter til frontend

internal fun Route.ReservasjonAdminApis() {
    val pepClient by inject<IPepClient>()
    val reservasjonApisTjeneste by inject<ReservasjonApisTjeneste>()

    get("/alle-reservasjoner") {
        medBrukerkontekst { kontekst ->
            if (pepClient.erOppgaveStyrer(kontekst)) {
                call.respond(reservasjonApisTjeneste.hentAlleAktiveReservasjoner(kontekst))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
