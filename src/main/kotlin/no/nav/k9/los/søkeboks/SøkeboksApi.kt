package no.nav.k9.los.søkeboks

import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.brukerkontekst.medBrukerkontekst
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import org.koin.ktor.ext.inject


fun Route.SøkeboksApi() {
    val søkeboksTjeneste by inject<SøkeboksTjeneste>()

    post(
        {
            description =
                "Søk etter oppgaver og tilhørende person. Dersom input er på 9 tegn antas den som journalpostId, ved 11 tegn som fødselsnummer, og ellers som fagsaknummer."
            request { body<SøkRequest>() }
            response {
                HttpStatusCode.OK to { body<Søkeresultat>() }
            }
        }
    ) {
        medBrukerkontekst { bruker ->
            if (bruker.harBasisTilgang) {
                val (søkeord) = call.receive<SøkRequest>()
                // Legacy-ruten ligger utenfor områdeApi og har derfor ikke noe {omrade}-segment
                // å lese. Den betjener kun K9; nye områder skal bruke SøkeboksApiNy.
                call.respond(søkeboksTjeneste.finnOppgaver(søkeord, Områder.K9, bruker))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
