package no.nav.k9.los.lagretsok

import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.kontekst.medBrukerkontekst
import no.nav.k9.los.infrastruktur.kontekst.Brukerkontekst
import kotlin.coroutines.coroutineContext
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.område
import org.koin.ktor.ext.inject

fun Route.LagretSøkApiNy() {
    val pepClient by inject<IPepClient>()
    val lagretSøkTjeneste by inject<LagretSøkTjeneste>()
    val lagretSøkRepository by inject<LagretSøkRepository>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()

    get({
        description = "Hent alle lagrede søk for innlogget saksbehandler."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
        }
        response {
            HttpStatusCode.OK to { body<List<LagretSøk>>() }
        }
    }) {
        medBrukerkontekst { kontekst ->
            if (pepClient.harBasisTilgang(kontekst)) {
                val område = kontekst.område
                val innloggetSaksbehandler = kontekst.bruker.navIdent.let {
                    saksbehandlerRepository.finnSaksbehandlerMedIdent(it, pepClient.erKode6Bruker(kontekst.bruker))
                }
                if (innloggetSaksbehandler == null) {
                    call.respond(HttpStatusCode.Forbidden, "Innlogget bruker er ikke i saksbehandler-tabellen.")
                } else {
                    val lagredeSøk = lagretSøkRepository.hentAlle(innloggetSaksbehandler)
                    call.respond(lagredeSøk)
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("{id}", {
        description = "Hent et lagret søk."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            pathParameter<Long>("id") {
                description = "Id til det lagrede søket"
                required = true
            }
        }
        response {
            HttpStatusCode.OK to { body<LagretSøk>() }
        }
    }) {
        medBrukerkontekst { kontekst ->
            if (pepClient.harBasisTilgang(kontekst)) {
                val område = kontekst.område
                val id = call.parameters["id"]!!.toLong()
                val innloggetSaksbehandler = kontekst.bruker.navIdent.let {
                    saksbehandlerRepository.finnSaksbehandlerMedIdent(it, pepClient.erKode6Bruker(kontekst.bruker))
                }
                if (innloggetSaksbehandler == null) {
                    call.respond(HttpStatusCode.Forbidden, "Innlogget bruker er ikke i saksbehandler-tabellen.")
                } else {
                    val lagretSøk = lagretSøkRepository.hent(id)
                    if (lagretSøk != null) {
                        if (lagretSøk.lagetAv != innloggetSaksbehandler.id) {
                            call.respond(HttpStatusCode.Forbidden)
                        } else {
                            call.respond(lagretSøk)
                        }
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("nytt", {
        description = "Lagre et nytt søk."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            body<NyttLagretSøkRequest> {
                description = "Tittel og spørring for det nye lagrede søket"
            }
        }
        response {
            HttpStatusCode.Created to { body<Long>() }
        }
    }) {
        medBrukerkontekst { kontekst ->
            if (pepClient.erOppgaveStyrer(kontekst)) {
                val område = kontekst.område
                val navIdent = kontekst.bruker.navIdent
                val request = call.receive<NyttLagretSøkRequest>()
                val lagretSøk = lagretSøkTjeneste.nytt(navIdent, request, pepClient.erKode6Bruker(kontekst.bruker))
                call.respond(HttpStatusCode.Created, lagretSøk)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("default-query", {
        description = "Hent standard spørring, avhengig av om innlogget bruker har tilgang til kode 6."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
        }
        response {
            HttpStatusCode.OK to { body<OppgaveQuery>() }
        }
    }) {
        medBrukerkontekst { kontekst ->
            if (pepClient.erOppgaveStyrer(kontekst)) {
                val område = kontekst.område
                val harKode6Tilgang = pepClient.harTilgangTilKode6(kontekst)
                call.respond(LagretSøk.defaultQuery(harKode6Tilgang))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    put("{id}/endre", {
        description = "Endre et eksisterende lagret søk."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            pathParameter<Long>("id") {
                description = "Id til det lagrede søket"
                required = true
            }
            body<EndreLagretSøkRequest> {
                description = "Nye verdier for det lagrede søket"
            }
        }
        response {
            HttpStatusCode.OK to { body<LagretSøk>() }
        }
    }) {
        medBrukerkontekst { kontekst ->
            if (pepClient.harBasisTilgang(kontekst)) {
                val område = kontekst.område
                val innloggetSaksbehandler = kontekst.bruker.navIdent.let {
                    saksbehandlerRepository.finnSaksbehandlerMedIdent(it, pepClient.erKode6Bruker(kontekst.bruker))
                }
                if (innloggetSaksbehandler == null) {
                    call.respond(HttpStatusCode.Forbidden, "Innlogget bruker er ikke i saksbehandler-tabellen.")
                } else {
                    val endreLagretSøk = call.receive<EndreLagretSøkRequest>()
                    val lagretSøk = lagretSøkTjeneste.endre(kontekst.bruker.navIdent, endreLagretSøk, pepClient.erKode6Bruker(kontekst.bruker))
                    call.respond(HttpStatusCode.OK, lagretSøk)
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("{id}/kopier", {
        description = "Kopier et eksisterende lagret søk."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            pathParameter<Long>("id") {
                description = "Id til det lagrede søket som skal kopieres"
                required = true
            }
            body<KopierLagretSøkRequest> {
                description = "Tittel på det nye, kopierte søket"
            }
        }
        response {
            HttpStatusCode.OK to { body<Long>() }
        }
    }) {
        medBrukerkontekst { kontekst ->
            if (pepClient.harBasisTilgang(kontekst)) {
                val område = kontekst.område
                val innloggetSaksbehandler = kontekst.bruker.navIdent.let {
                    saksbehandlerRepository.finnSaksbehandlerMedIdent(it, pepClient.erKode6Bruker(kontekst.bruker))
                }
                if (innloggetSaksbehandler == null) {
                    call.respond(HttpStatusCode.Forbidden, "Innlogget bruker er ikke i saksbehandler-tabellen.")
                } else {
                    val (tittel) = call.receive<KopierLagretSøkRequest>()
                    val lagretSøkId = call.parameters["id"]!!.toLong()
                    val nyttLagretSøk = lagretSøkTjeneste.kopier(kontekst.bruker.navIdent, lagretSøkId, tittel, pepClient.erKode6Bruker(kontekst.bruker))
                    call.respond(HttpStatusCode.OK, nyttLagretSøk)
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    delete("{id}/slett", {
        description = "Slett et lagret søk."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            pathParameter<Long>("id") {
                description = "Id til det lagrede søket"
                required = true
            }
        }
        response {
            HttpStatusCode.OK to { body<Unit>() }
        }
    }) {
        medBrukerkontekst { kontekst ->
            if (pepClient.harBasisTilgang(kontekst)) {
                val område = kontekst.område
                val innloggetSaksbehandler = kontekst.bruker.navIdent.let {
                    saksbehandlerRepository.finnSaksbehandlerMedIdent(it, pepClient.erKode6Bruker(kontekst.bruker))
                }
                if (innloggetSaksbehandler == null) {
                    call.respond(HttpStatusCode.Forbidden, "Innlogget bruker er ikke i saksbehandler-tabellen.")
                } else {
                    val lagretSøkId = call.parameters["id"]!!.toLong()
                    lagretSøkTjeneste.slett(kontekst.bruker.navIdent, lagretSøkId, pepClient.erKode6Bruker(kontekst.bruker))
                    call.respond(HttpStatusCode.OK)
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/{id}/antall", {
        description = "Hent antall oppgaver som matcher et lagret søk."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            pathParameter<Long>("id") {
                description = "Id til det lagrede søket"
                required = true
            }
        }
    }) {
        medBrukerkontekst { kontekst ->
            if (pepClient.harBasisTilgang(kontekst)) {
                val område = kontekst.område
                val lagretSøkId = call.parameters["id"]!!
                val innloggetSaksbehandler = kontekst.bruker.navIdent.let {
                    saksbehandlerRepository.finnSaksbehandlerMedIdent(it, pepClient.erKode6Bruker(kontekst.bruker))
                }
                if (innloggetSaksbehandler == null) {
                    call.respond(HttpStatusCode.Forbidden, "Innlogget bruker er ikke i saksbehandler-tabellen.")
                } else {
                    val lagretSøk = lagretSøkRepository.hent(lagretSøkId.toLong())
                    if (lagretSøk == null) {
                        call.respond(HttpStatusCode.NotFound)
                    } else if (lagretSøk.lagetAv != innloggetSaksbehandler.id) {
                        call.respond(HttpStatusCode.Forbidden)
                    } else {
                        call.respond(lagretSøkTjeneste.hentAntall(lagretSøkId.toLong()))
                    }
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
