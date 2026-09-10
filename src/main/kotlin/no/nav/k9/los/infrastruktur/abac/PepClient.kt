package no.nav.k9.los.infrastruktur.abac

import no.nav.k9.los.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstMedOmråde
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import no.nav.sif.abac.kontrakt.abac.dto.SaksnummerDto
import no.nav.sif.abac.kontrakt.person.AktørId
import org.slf4j.LoggerFactory

class PepClient(
    private val azureGraphService: IAzureGraphService,
    private val sifAbacPdpKlienter: SifAbacPdpKlienter,
) : IPepClient {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun diskresjonskoderForSak(fagsakNummer: String, område: Områder): Set<Diskresjonskode> =
        sifAbacPdpKlienter.forOmråde(område).diskresjonskoderSak(SaksnummerDto(fagsakNummer))

    override suspend fun diskresjonskoderForPerson(aktørId: String, område: Områder): Set<Diskresjonskode> =
        sifAbacPdpKlienter.forOmråde(område).diskresjonskoderPerson(AktørId(aktørId))

    override suspend fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        brukerkontekst: BrukerkontekstMedOmråde,
        action: Action,
    ): Boolean {
        brukerkontekst.krevOmråde(oppgave.oppgavetype.område.tilOmrådeEnum())
        return harTilgang(oppgave, brukerkontekst.område, action, brukerkontekst.navIdent, brukerkontekst.idToken)
    }

    override suspend fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        område: Områder,
        saksbehandler: Saksbehandler,
        action: Action,
    ): Boolean {
        require(område == oppgave.oppgavetype.område.tilOmrådeEnum()) { "Oppgaven tilhører et annet område" }
        return harTilgang(oppgave, område, action, requireNotNull(saksbehandler.navident))
    }

    private suspend fun harTilgang(
        oppgave: Oppgave,
        område: Områder,
        action: Action,
        ident: String,
        idToken: IIdToken? = null,
    ): Boolean {
        val klient = sifAbacPdpKlienter.forOmråde(område)
        val oppgavetype = oppgave.oppgavetype.eksternId
        if (område == Områder.K9 && oppgavetype !in setOf("k9sak", "k9klage", "k9tilbake", "k9punsj")) {
            throw NotImplementedError("Ukjent oppgavetype for tilgangskontroll i K9")
        }
        val saksnummer = oppgave.hentVerdi("saksnummer")
        if (oppgavetype != "k9punsj" && !saksnummer.isNullOrBlank()) {
            return if (idToken != null) {
                klient.harTilgangTilSak(action, SaksnummerDto(saksnummer), idToken)
            } else {
                klient.harTilgangTilSak(action, SaksnummerDto(saksnummer), ident, azureGraphService.hentGrupper(ident))
            }
        }
        if (område == Områder.K9 && oppgavetype != "k9punsj") return false

        val aktørIder = setOfNotNull(oppgave.hentVerdi("aktorId"), oppgave.hentVerdi("pleietrengendeAktorId"))
            .map { AktørId(it) }
        if (aktørIder.isEmpty()) {
            // Beholder masters særregel kun for K9-punsj, aldri som aktivitetspenger-fallback.
            if (område == Områder.K9 && oppgavetype == "k9punsj") {
                log.warn("Ingen aktørIder funnet for punsj-oppgave. Gir tilgang for å unngå at den havner utenfor alle køer.")
                return true
            }
            return false
        }
        return if (idToken != null) {
            klient.harTilgangTilPersoner(action, aktørIder, idToken)
        } else {
            klient.harTilgangTilPersoner(action, aktørIder, ident, azureGraphService.hentGrupper(ident))
        }
    }
}
