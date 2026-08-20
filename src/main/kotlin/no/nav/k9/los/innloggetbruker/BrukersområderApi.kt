package no.nav.k9.los.innloggetbruker

import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.kontekst.medInnloggetBruker
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.koin.ktor.ext.inject

internal fun Route.BrukersområderApi() {
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
    val pepClient by inject<IPepClient>()

    get {
        medInnloggetBruker { kontekst ->
            val skjermet = pepClient.erKode6Bruker(kontekst)
            val områder: List<Områder> = (
                    saksbehandlerRepository.finnSaksbehandlerMedIdent(kontekst.bruker.navIdent, skjermet)
                        ?: saksbehandlerRepository.finnSaksbehandlerMedEpost(kontekst.bruker.idToken.getUsername(), skjermet)
                    )
                ?.områder
                ?.sortedBy { it.eksternId }
                ?: emptyList()

            call.respond(områder)
        }
    }
}
