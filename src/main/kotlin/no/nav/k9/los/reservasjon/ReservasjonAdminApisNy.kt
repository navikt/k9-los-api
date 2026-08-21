package no.nav.k9.los.reservasjon

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.brukerkontekst.medBrukerkontekst
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import org.koin.ktor.ext.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("nav.OppgaveApisNy")

//TODO generell sikring kode6 - se etter feil
//TODO fjern reservasjonsid fra objekter til frontend

internal fun Route.ReservasjonAdminApisNy() {
    val pepClient by inject<IPepClient>()
    val reservasjonApisTjeneste by inject<ReservasjonApisTjeneste>()

    get("/alle-reservasjoner", {
        description = "Hent alle aktive reservasjoner."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
        }
    }) {
        medBrukerkontekst { kontekst ->
            if (pepClient.erOppgaveStyrer(kontekst)) {
                call.respond(reservasjonApisTjeneste.hentAlleAktiveReservasjoner(kontekst))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
