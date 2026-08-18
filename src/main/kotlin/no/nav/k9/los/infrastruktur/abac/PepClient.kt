package no.nav.k9.los.infrastruktur.abac

import kotlinx.coroutines.runBlocking
import no.nav.k9.los.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.infrastruktur.rest.idToken
import no.nav.k9.los.infrastruktur.rest.område
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

class PepClient internal constructor(
    private val azureGraphService: IAzureGraphService,
    private val sifAbacPdpKlienter: SifAbacPdpKlienter,
    private val gruppeoppsett: Gruppeoppsett = Gruppeoppsett()
) : IPepClient {
    private val log: Logger = LoggerFactory.getLogger(PepClient::class.java)

    override suspend fun erOppgaveStyrer(): Boolean {
        val område = coroutineContext.område()
        return iGruppe(gruppeoppsett.forOmråde(område).oppgavestyrer)
    }

    override suspend fun harBasisTilgang(): Boolean {
        val område = coroutineContext.område()
        val grupper = gruppeoppsett.forOmråde(område)
        return iGruppe(grupper.saksbehandler) || iGruppe(grupper.veileder)
    }

    override suspend fun kanLeggeUtDriftsmelding(): Boolean {
        // Drift-gruppen er global for Los og ikke knyttet til området ruten kjører under.
        return iGruppe(gruppeoppsett.drift)
    }

    override suspend fun harTilgangTilReserveringAvOppgaver(): Boolean {
        val område = coroutineContext.område()
        return iGruppe(gruppeoppsett.forOmråde(område).saksbehandler)
    }

    override suspend fun harTilgangTilKode6(ident: String): Boolean {
        val område = coroutineContext.område()
        if (ident == coroutineContext.idToken().getNavIdent()) {
            return harTilgangTilKode6()
        }
        val kode6Gruppe = gruppeoppsett.forOmråde(område).kode6 ?: return false
        val grupper = azureGraphService.hentGrupperForSaksbehandler(ident)
        return grupper.contains(kode6Gruppe)
    }

    override suspend fun harTilgangTilKode6(): Boolean {
        val område = coroutineContext.område()
        return iGruppe(gruppeoppsett.forOmråde(område).kode6)
    }

    private suspend fun iGruppe(gruppeId: UUID?): Boolean =
        gruppeId?.toString()?.let(coroutineContext.idToken().groups::contains) ?: false

    override suspend fun erSakKode6(fagsakNummer: String): Boolean {
        val område = coroutineContext.område()
        krevTilgjengelig(område)
        val diskresjonskoder = sifAbacPdpKlienter.forOmråde(område).diskresjonskoderSak(SaksnummerDto(fagsakNummer))
        return diskresjonskoder.contains(Diskresjonskode.KODE6)
    }

    override suspend fun erAktørKode6(aktørid: String): Boolean {
        val område = coroutineContext.område()
        krevTilgjengelig(område)
        val diskresjonskoder = sifAbacPdpKlienter.forOmråde(område).diskresjonskoderPerson(AktørId(aktørid))
        return diskresjonskoder.contains(Diskresjonskode.KODE6)
    }

    override suspend fun diskresjonskoderForSak(fagsakNummer: String): Set<Diskresjonskode> {
        return diskresjonskoderForSak(fagsakNummer, coroutineContext.område())
    }

    override suspend fun diskresjonskoderForSak(fagsakNummer: String, område: Områder): Set<Diskresjonskode> {
        krevTilgjengelig(område)
        return sifAbacPdpKlienter.forOmråde(område).diskresjonskoderSak(SaksnummerDto(fagsakNummer))
    }

    override suspend fun diskresjonskoderForPerson(aktørId: String): Set<Diskresjonskode> {
        return diskresjonskoderForPerson(aktørId, coroutineContext.område())
    }

    override suspend fun diskresjonskoderForPerson(aktørId: String, område: Områder): Set<Diskresjonskode> {
        krevTilgjengelig(område)
        return sifAbacPdpKlienter.forOmråde(område).diskresjonskoderPerson(AktørId(aktørId))
    }

    override suspend fun erSakKode7EllerEgenAnsatt(fagsakNummer: String): Boolean {
        val område = coroutineContext.område()
        krevTilgjengelig(område)
        val diskresjonskoder = sifAbacPdpKlienter.forOmråde(område).diskresjonskoderSak(SaksnummerDto(fagsakNummer))
        return diskresjonskoder.contains(Diskresjonskode.KODE7) || diskresjonskoder.contains(Diskresjonskode.SKJERMET)
    }

    override suspend fun erAktørKode7EllerEgenAnsatt(aktørid: String): Boolean {
        val område = coroutineContext.område()
        krevTilgjengelig(område)
        val diskresjonskoder = sifAbacPdpKlienter.forOmråde(område).diskresjonskoderPerson(AktørId(aktørid))
        return diskresjonskoder.contains(Diskresjonskode.KODE7) || diskresjonskoder.contains(Diskresjonskode.SKJERMET)
    }

    override suspend fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        action: Action,
        grupperForSaksbehandler: Set<UUID>?,
    ): Boolean {
        val område = coroutineContext.område()
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
        action: Action
    ): Boolean {
        val område = oppgave.oppgavetype.område.tilOmrådeEnum()
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
                    grupperForSaksbehandler ?: azureGraphService.hentGrupperForSaksbehandler(identTilInnloggetBruker)
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
                    grupperForSaksbehandler ?: azureGraphService.hentGrupperForSaksbehandler(identTilInnloggetBruker)
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

    private fun krevTilgjengelig(område: Områder) {
        if (område == Områder.UNG) throw OmrådeIkkeTilgjengeligException(område)
    }

}
