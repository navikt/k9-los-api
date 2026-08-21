package no.nav.k9.los.infrastruktur.abac

import no.nav.k9.los.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstMedOmråde
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import no.nav.sif.abac.kontrakt.abac.dto.SaksnummerDto
import no.nav.sif.abac.kontrakt.person.AktørId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

class PepClient internal constructor(
    private val azureGraphService: IAzureGraphService,
    private val sifAbacPdpKlienter: SifAbacPdpKlienter,
    private val gruppeoppsett: Gruppeoppsett,
) : IPepClient {
    private val log: Logger = LoggerFactory.getLogger(PepClient::class.java)

    override suspend fun harSaksbehandlerTilgangTilKode6(ident: String, brukerkontekst: BrukerkontekstMedOmråde): Boolean {
        if (ident == brukerkontekst.navIdent) {
            return brukerkontekst.harTilgangTilKode6
        }
        val kode6Gruppe = gruppeoppsett.forOmråde(brukerkontekst.område).kode6 ?: return false
        val grupper = azureGraphService.hentGrupper(ident)
        return grupper.contains(kode6Gruppe)
    }

    override suspend fun diskresjonskoderForSak(fagsakNummer: String, område: Områder): Set<Diskresjonskode> {
        return sifAbacPdpKlienter.forOmråde(område).diskresjonskoderSak(SaksnummerDto(fagsakNummer))
    }

    override suspend fun diskresjonskoderForPerson(aktørId: String, område: Områder): Set<Diskresjonskode> {
        return sifAbacPdpKlienter.forOmråde(område).diskresjonskoderPerson(AktørId(aktørId))
    }

    override suspend fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        bruker: BrukerkontekstMedOmråde,
        action: Action,
    ): Boolean {
        return harTilgang(
            område = oppgave.oppgavetype.område.tilOmrådeEnum(),
            oppgavetype = oppgave.oppgavetype.eksternId,
            identTilInnloggetBruker = bruker.navIdent,
            action = action,
            saksnummer = oppgave.hentVerdi("saksnummer"),
            aktørIdSøker = oppgave.hentVerdi("aktorId"),
            aktørIdPleietrengende = oppgave.hentVerdi("pleietrengendeAktorId"),
            grupperForSaksbehandler = bruker.grupper,
        )
    }

    override suspend fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        område: Områder,
        saksbehandler: Saksbehandler,
        action: Action
    ): Boolean {
        return harTilgang(
            område = område,
            oppgavetype = oppgave.oppgavetype.eksternId,
            identTilInnloggetBruker = saksbehandler.navident!!,
            action = action,
            saksnummer = oppgave.hentVerdi("saksnummer"),
            aktørIdSøker = oppgave.hentVerdi("aktorId"),
            aktørIdPleietrengende = oppgave.hentVerdi("pleietrengendeAktorId"),
        )
    }

    private suspend fun harTilgang(
        område: Områder,
        oppgavetype: String,
        identTilInnloggetBruker: String,
        action: Action,
        saksnummer: String?,
        aktørIdSøker: String?,
        aktørIdPleietrengende: String?,
        grupperForSaksbehandler: Set<UUID>? = null
    ): Boolean = when (område) {
        Områder.K9 -> harTilgangK9(
            oppgavetype = oppgavetype,
            identTilInnloggetBruker = identTilInnloggetBruker,
            action = action,
            saksnummer = saksnummer,
            aktørIdSøker = aktørIdSøker,
            aktørIdPleietrengende = aktørIdPleietrengende,
            grupperForSaksbehandler = grupperForSaksbehandler
        )

        Områder.UNG -> false
    }

    private suspend fun harTilgangK9(
        oppgavetype: String,
        identTilInnloggetBruker: String,
        action: Action,
        saksnummer: String?,
        aktørIdSøker: String?,
        aktørIdPleietrengende: String?,
        grupperForSaksbehandler: Set<UUID>? = null
    ): Boolean {
        val klient = sifAbacPdpKlienter.forOmråde(Områder.K9)
        return when (oppgavetype) {
            "k9sak", "k9klage", "k9tilbake" -> {
                //TODO når abac-k9 er ryddet bort: vurder å bruk sifAbacPdpKlient.harTilgangTilSak(action, saksnummer) de steder hvor vi sjekker innlogget bruker
                val saksbehandlersGrupper =
                    grupperForSaksbehandler ?: azureGraphService.hentGrupper(identTilInnloggetBruker)
                val tilgang = klient.harTilgangTilSak(
                    action = action,
                    saksnummerDto = SaksnummerDto(saksnummer!!),
                    saksbehandlersIdent = identTilInnloggetBruker,
                    saksbehandlersGrupper = saksbehandlersGrupper
                )

                tilgang
            }

            "k9punsj" -> {
                val berørteAktørId = setOfNotNull(aktørIdSøker, aktørIdPleietrengende)
                val aktørIder = berørteAktørId.map { AktørId(it) }
                val saksbehandlersGrupper =
                    grupperForSaksbehandler ?: azureGraphService.hentGrupper(identTilInnloggetBruker)
                val tilgang = if (aktørIder.isNotEmpty()) klient.harTilgangTilPersoner(
                    action = action,
                    aktørIder = aktørIder,
                    saksbehandlersIdent = identTilInnloggetBruker,
                    saksbehandlersGrupper = saksbehandlersGrupper
                ) else {
                    log.warn("Ingen aktørIder funnet for punsj-oppgave. Gir som fallback tilgang til oppgaven, for å unngå at den havner utenfor alle køer.")
                    true
                }

                tilgang
            }

            else -> throw NotImplementedError("Støtter kun tilgangsoppslag på k9klage, k9sak, k9tilbake og k9punsj")
        }
    }
}
