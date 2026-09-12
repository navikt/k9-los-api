package no.nav.k9.los.infrastruktur.abac

import no.nav.k9.los.infrastruktur.abac.tilganger.Tilganger
import no.nav.k9.los.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import no.nav.k9.los.infrastruktur.rest.idToken
import no.nav.k9.los.infrastruktur.rest.område
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import no.nav.sif.abac.kontrakt.abac.dto.SaksnummerDto
import no.nav.sif.abac.kontrakt.person.AktørId
import org.slf4j.LoggerFactory
import kotlin.coroutines.coroutineContext

class PepClient(
    private val azureGraphService: IAzureGraphService,
    private val sifAbacPdpKlientK9: SifAbacPdpKlientK9,
    private val sifAbacPdpKlientAktivitetspenger: SifAbacPdpKlientAktivitetspenger,
) : IPepClient {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun diskresjonskoderForSak(fagsakNummer: String, område: Områder): Set<Diskresjonskode> =
        when (område) {
            Områder.K9 -> sifAbacPdpKlientK9.diskresjonskoderSak(SaksnummerDto(fagsakNummer))
            Områder.AKTIVITETSPENGER -> sifAbacPdpKlientAktivitetspenger.diskresjonskoderSak(SaksnummerDto(fagsakNummer))
        }

    override suspend fun diskresjonskoderForPerson(aktørId: String, område: Områder): Set<Diskresjonskode> =
        when (område) {
            Områder.K9 -> sifAbacPdpKlientK9.diskresjonskoderPerson(AktørId(aktørId))
            Områder.AKTIVITETSPENGER -> sifAbacPdpKlientAktivitetspenger.diskresjonskoderPerson(AktørId(aktørId))
        }

    private suspend fun tilganger(område: Områder): Tilganger {
        return when (område) {
            Områder.K9 -> sifAbacPdpKlientK9.hentTilganger(coroutineContext.idToken())
            Områder.AKTIVITETSPENGER -> sifAbacPdpKlientAktivitetspenger.hentTilganger(coroutineContext.idToken())
        }
    }

    override suspend fun kanLeggeUtDriftsmelding(): Boolean = tilganger(coroutineContext.område()).drift
    override suspend fun harBasisTilgang(): Boolean = tilganger(coroutineContext.område()).basis
    override suspend fun erOppgaveStyrer(): Boolean = tilganger(coroutineContext.område()).oppgavestyring
    override suspend fun harTilgangTilKode6(): Boolean = tilganger(coroutineContext.område()).kode6
    override suspend fun harTilgangTilReserveringAvOppgaver(): Boolean =
        tilganger(coroutineContext.område()).reservering
    override suspend fun basisTilgangIOmråder(): Set<Områder> {
        return Områder.entries.filter { tilganger(it).basis }.toSet()
    }

    // Tilgang til oppgave, for innlogget bruker
    override suspend fun harTilgangTilOppgaveV3(oppgave: Oppgave, action: Action): Boolean {
        val område = coroutineContext.område()
        val idToken = coroutineContext.idToken()
        return harTilgangTilOppgaveV3(område, idToken, oppgave, action)
    }

    override suspend fun harTilgangTilOppgaveV3(område: Områder, idToken: IIdToken, oppgave: Oppgave, action: Action): Boolean {
        require(område == oppgave.oppgavetype.område.tilOmråderEnum()) { "Oppgaven tilhører et annet område" }

        when (område) {
            Områder.K9 -> {
                val oppgavetype = oppgave.oppgavetype.eksternId
                val saksnummer = oppgave.hentVerdi("saksnummer")
                if (!saksnummer.isNullOrBlank()) {
                    return sifAbacPdpKlientK9.harTilgangTilSak(action, SaksnummerDto(saksnummer), idToken)
                } else if (oppgavetype == "k9punsj") {
                    val aktørIder =
                        setOfNotNull(oppgave.hentVerdi("aktorId"), oppgave.hentVerdi("pleietrengendeAktorId"))
                            .map { AktørId(it) }
                    if (aktørIder.isEmpty()) {
                        log.warn("Ingen aktørIder funnet for punsj-oppgave. Gir tilgang for å unngå at den havner utenfor alle køer.")
                        return true
                    }
                    return sifAbacPdpKlientK9.harTilgangTilPersoner(action, aktørIder, idToken)
                } else {
                    return false
                }
            }

            Områder.AKTIVITETSPENGER -> {
                val saksnummer = oppgave.hentVerdi("saksnummer")
                return !saksnummer.isNullOrBlank() && sifAbacPdpKlientK9.harTilgangTilSak(
                    action,
                    SaksnummerDto(saksnummer),
                    idToken
                )
            }
        }
    }

    // Tilgang til oppgave, for en annen saksbehandler
    override suspend fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        saksbehandler: Saksbehandler,
        action: Action
    ): Boolean {
        return harTilgangTilOppgaveV3(oppgave, coroutineContext.område(), saksbehandler, action)
    }

    override suspend fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        område: Områder,
        saksbehandler: Saksbehandler,
        action: Action,
    ): Boolean {
        require(område == oppgave.oppgavetype.område.tilOmråderEnum()) { "Oppgaven tilhører et annet område" }
        val ident = checkNotNull(saksbehandler.navident) { "Saksbehandler må ha navident" }

        when (område) {
            Områder.K9 -> {
                val oppgavetype = oppgave.oppgavetype.eksternId
                val saksnummer = oppgave.hentVerdi("saksnummer")
                if (!saksnummer.isNullOrBlank()) {
                    return sifAbacPdpKlientK9.harTilgangTilSak(
                        action,
                        SaksnummerDto(saksnummer),
                        saksbehandler.navident,
                        azureGraphService.hentGrupperForSaksbehandler(ident)
                    )
                } else if (oppgavetype == "k9punsj") {
                    val aktørIder =
                        setOfNotNull(oppgave.hentVerdi("aktorId"), oppgave.hentVerdi("pleietrengendeAktorId"))
                            .map { AktørId(it) }
                    if (aktørIder.isEmpty()) {
                        log.warn("Ingen aktørIder funnet for punsj-oppgave. Gir tilgang for å unngå at den havner utenfor alle køer.")
                        return true
                    }
                    return sifAbacPdpKlientK9.harTilgangTilPersoner(
                        action,
                        aktørIder,
                        ident,
                        azureGraphService.hentGrupperForSaksbehandler(ident)
                    )
                } else {
                    return false
                }
            }

            Områder.AKTIVITETSPENGER -> {
                log.warn("Forsøker å gjøre tilgangssjekk for andre saksbehandlere, men aktivitetspenger er ikke støttet")
                throw NotImplementedError("Kan ikke tilgangssjekke for andre saksbehandlere på aktivitetspenger")
            }
        }
    }
}
