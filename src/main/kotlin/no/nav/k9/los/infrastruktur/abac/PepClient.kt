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
    ): Boolean = when (område) {
        Områder.K9 -> harTilgangK9(
            oppgavetype = oppgavetype,
            identTilInnloggetBruker = identTilInnloggetBruker,
            action = action,
            saksnummer = saksnummer,
            aktørIdSøker = aktørIdSøker,
            aktørIdPleietrengende = aktørIdPleietrengende,
        )

        Områder.AKTIVITETSPENGER -> false
    }

    private suspend fun harTilgangK9(
        oppgavetype: String,
        identTilInnloggetBruker: String,
        action: Action,
        saksnummer: String?,
        aktørIdSøker: String?,
        aktørIdPleietrengende: String?,
    ): Boolean {
        val klient = sifAbacPdpKlienter.forOmråde(Områder.K9)
        // TODO: PDP-endepunktene sak-grupper/personer-grupper tar fortsatt grupper som input.
        // Når PDP tilbyr ident-baserte endepunkter (gruppene hentes da fra OBO-tokenet
        // server-side) kan Graph-oppslaget her fjernes.
        val saksbehandlersGrupper = azureGraphService.hentGrupper(identTilInnloggetBruker)
        return when (oppgavetype) {
            "k9sak", "k9klage", "k9tilbake" -> {
                klient.harTilgangTilSak(
                    action = action,
                    saksnummerDto = SaksnummerDto(saksnummer!!),
                    saksbehandlersIdent = identTilInnloggetBruker,
                    saksbehandlersGrupper = saksbehandlersGrupper
                )
            }

            "k9punsj" -> {
                val berørteAktørId = setOfNotNull(aktørIdSøker, aktørIdPleietrengende)
                val aktørIder = berørteAktørId.map { AktørId(it) }
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
