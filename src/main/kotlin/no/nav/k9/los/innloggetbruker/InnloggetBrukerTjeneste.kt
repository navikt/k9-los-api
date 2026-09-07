package no.nav.k9.los.innloggetbruker

import kotlinx.coroutines.CancellationException
import no.nav.k9.los.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstMedOmråde
import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstUtenOmråde
import no.nav.k9.los.infrastruktur.idtoken.IdToken
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDateTime

class InnloggetBrukerTjeneste(
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val azureGraphService: IAzureGraphService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(InnloggetBrukerTjeneste::class.java)

    fun finnSaksbehandler(navIdent: String, epost: String, skjermet: Boolean): Saksbehandler? =
        saksbehandlerRepository.finnSaksbehandlerMedIdent(navIdent, skjermet)
            ?: saksbehandlerRepository.finnSaksbehandlerMedEpost(epost, skjermet)

    suspend fun hentInnloggetBruker(
        bruker: BrukerkontekstMedOmråde,
    ): InnloggetBrukerDto {
        val saksbehandler = finnOgVedlikehold(
            navIdent = bruker.navIdent,
            token = bruker.idToken,
            kode6 = bruker.harTilgangTilKode6,
        )
        return InnloggetBrukerDto(
            navn = bruker.idToken.getName(),
            brukerIdent = bruker.navIdent,
            id = saksbehandler?.id,
            harBasisTilgang = bruker.harBasisTilgang,
            kanOppgavestyre = bruker.erOppgavestyrer,
            kanReservere = bruker.harTilgangTilReserveringAvOppgaver,
            kanDrifte = bruker.harDriftstilgang,
            finnesISaksbehandlerTabell = saksbehandler != null,
        )
    }

    suspend fun hentLegacyInnloggetBruker(bruker: BrukerkontekstMedOmråde): LegacyInnloggetBrukerDto {
        val saksbehandler = finnOgVedlikehold(
            navIdent = bruker.navIdent,
            token = bruker.idToken,
            kode6 = bruker.harTilgangTilKode6,
        )
        return LegacyInnloggetBrukerDto(
            brukernavn = bruker.idToken.getPreferredUsername(),
            navn = bruker.idToken.getName(),
            brukerIdent = bruker.navIdent,
            id = saksbehandler?.id,
            kanSaksbehandle = bruker.harBasisTilgang,
            kanOppgavestyre = bruker.erOppgavestyrer,
            kanReservere = bruker.harTilgangTilReserveringAvOppgaver,
            kanDrifte = bruker.harDriftstilgang,
            finnesISaksbehandlerTabell = saksbehandler != null,
            områder = saksbehandler?.områder ?: emptyList(),
        )
    }

    suspend fun hentBrukersOmråder(bruker: BrukerkontekstUtenOmråde): List<Områder> {
        finnOgVedlikehold(
            navIdent = bruker.navIdent,
            token = bruker.idToken,
            kode6 = bruker.harKode6TilgangIEttEllerFlereOmråder,
        )
        return bruker.områderMedBasisTilgang
    }

    private suspend fun finnOgVedlikehold(navIdent: String, token: IdToken, kode6: Boolean): Saksbehandler? {
        val saksbehandler = finnSaksbehandler(navIdent, token.getPreferredUsername(), kode6)
        if (saksbehandler == null) {
            log.warn("Innlogget saksbehandler finnes ikke i saksbehandlertabellen og kan derfor ikke vedlikeholdes")
        } else {
            vedlikeholdHvisUtdatert(saksbehandler, navIdent, token, kode6)
        }
        return saksbehandler
    }

    private suspend fun vedlikeholdHvisUtdatert(
        saksbehandler: Saksbehandler,
        navIdent: String,
        token: IdToken,
        kode6: Boolean,
    ) {
        val nå = LocalDateTime.now(clock)
        val sistOppdatert = saksbehandler.sistOppdatert
        if (sistOppdatert != null && !sistOppdatert.isBefore(nå.minusHours(24))) {
            return
        }

        val enhet = try {
            azureGraphService.hentEnhet(navIdent, token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Kunne ikke hente enhet for innlogget saksbehandler. Forsøker igjen ved neste innlogging", e)
            // TODO: Oppdatere felter fra token selv om enhetskall feiler?
            return
        }

        saksbehandlerRepository.vedlikeholdSaksbehandler(
            saksbehandler = Saksbehandler(
                id = saksbehandler.id,
                navident = navIdent,
                navn = token.getName(),
                epost = token.getPreferredUsername(),
                enhet = enhet,
                områder = saksbehandler.områder,
                kode6 = kode6,
            ),
            skjermet = kode6,
            oppdatertTidspunkt = nå,
        )
    }
}
