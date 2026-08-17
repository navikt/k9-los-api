package no.nav.k9.los.infrastruktur.abac

import kotlinx.coroutines.runBlocking
import no.nav.k9.los.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.infrastruktur.rest.idToken
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import no.nav.sif.abac.kontrakt.abac.dto.SaksnummerDto
import no.nav.sif.abac.kontrakt.person.AktørId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.coroutines.coroutineContext

class PepClient(
    private val azureGraphService: IAzureGraphService,
    private val sifAbacPdpKlienter: SifAbacPdpKlienter,
) : IPepClient {
    private val log: Logger = LoggerFactory.getLogger(PepClient::class.java)

    private suspend fun idToken(område: Områder) = coroutineContext.idToken()

    override suspend fun erOppgaveStyrer(område: Områder): Boolean {
        //TODO inline metode
        return idToken(område).erOppgavebehandler()
    }

    override suspend fun harBasisTilgang(område: Områder): Boolean {
        //TODO inline metode
        return idToken(område).harBasistilgang()
    }

    override suspend fun kanLeggeUtDriftsmelding(område: Områder): Boolean {
        //TODO inline metode
        return idToken(område).erDrifter()
    }

    override suspend fun harTilgangTilReserveringAvOppgaver(område: Områder): Boolean {
        //TODO inline metode
        return idToken(område).erSaksbehandler()
    }

    override suspend fun harTilgangTilKode6(ident: String, område: Områder): Boolean {
        if (ident == coroutineContext.idToken().getNavIdent()) {
            return harTilgangTilKode6(område)
        }
        val grupper = azureGraphService.hentGrupperForSaksbehandler(ident)
        return grupper.contains(UUID.fromString(kode6GruppeId(område)))
    }

    private fun kode6GruppeId(område: Områder): String = when (område) {
        Områder.K9 -> System.getenv("BRUKER_GRUPPE_ID_KODE6")
        Områder.UNG -> throw NotImplementedError("Gruppetilganger for område UNG er ikke implementert ennå (kode6)")
    }

    override suspend fun harTilgangTilKode6(område: Områder): Boolean {
        //TODO inline metode
        return idToken(område).kanBehandleKode6()
    }

    override suspend fun erSakKode6(fagsakNummer: String, område: Områder): Boolean {
        val diskresjonskoder = sifAbacPdpKlienter.forOmråde(område).diskresjonskoderSak(SaksnummerDto(fagsakNummer))
        return diskresjonskoder.contains(Diskresjonskode.KODE6)
    }

    override suspend fun erAktørKode6(aktørid: String, område: Områder): Boolean {
        val diskresjonskoder = sifAbacPdpKlienter.forOmråde(område).diskresjonskoderPerson(AktørId(aktørid))
        return diskresjonskoder.contains(Diskresjonskode.KODE6)
    }

    override suspend fun diskresjonskoderForSak(fagsakNummer: String, område: Områder): Set<Diskresjonskode> {
        return sifAbacPdpKlienter.forOmråde(område).diskresjonskoderSak(SaksnummerDto(fagsakNummer))
    }

    override suspend fun diskresjonskoderForPerson(aktørId: String, område: Områder): Set<Diskresjonskode> {
        return sifAbacPdpKlienter.forOmråde(område).diskresjonskoderPerson(AktørId(aktørId))
    }

    override suspend fun erSakKode7EllerEgenAnsatt(fagsakNummer: String, område: Områder): Boolean {
        val diskresjonskoder = sifAbacPdpKlienter.forOmråde(område).diskresjonskoderSak(SaksnummerDto(fagsakNummer))
        return diskresjonskoder.contains(Diskresjonskode.KODE7) || diskresjonskoder.contains(Diskresjonskode.SKJERMET)
    }

    override suspend fun erAktørKode7EllerEgenAnsatt(aktørid: String, område: Områder): Boolean {
        val diskresjonskoder = sifAbacPdpKlienter.forOmråde(område).diskresjonskoderPerson(AktørId(aktørid))
        return diskresjonskoder.contains(Diskresjonskode.KODE7) || diskresjonskoder.contains(Diskresjonskode.SKJERMET)
    }

    override suspend fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        action: Action,
        grupperForSaksbehandler: Set<UUID>?,
        område: Områder
    ): Boolean {
        return harTilgang(
            område = område,
            oppgavetype = oppgave.oppgavetype.eksternId,
            identTilInnloggetBruker = azureGraphService.hentIdentTilInnloggetBruker(),
            action = action,
            saksnummer = oppgave.hentVerdi("saksnummer"),
            aktørIdSøker = oppgave.hentVerdi("aktorId"),
            aktørIdPleietrengende = oppgave.hentVerdi("pleietrengendeAktorId"),
            grupperForSaksbehandler = grupperForSaksbehandler
        )
    }

    override fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        saksbehandler: Saksbehandler,
        action: Action,
        område: Områder
    ): Boolean {
        return runBlocking {
            harTilgang(
                område = område,
                oppgavetype = oppgave.oppgavetype.eksternId,
                identTilInnloggetBruker = saksbehandler.navident!!,
                action = action,
                saksnummer = oppgave.hentVerdi("saksnummer"),
                aktørIdSøker = oppgave.hentVerdi("aktorId"),
                aktørIdPleietrengende = oppgave.hentVerdi("pleietrengendeAktorId"),
            )
        }
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

        Områder.UNG -> throw NotImplementedError(
            "Tilgangskontroll for område UNG er ikke implementert ennå (oppgavetype $oppgavetype)"
        )
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
                val saksbehandlersGrupper = grupperForSaksbehandler ?: azureGraphService.hentGrupperForSaksbehandler(identTilInnloggetBruker)
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
                val saksbehandlersGrupper = grupperForSaksbehandler ?: azureGraphService.hentGrupperForSaksbehandler(identTilInnloggetBruker)
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

