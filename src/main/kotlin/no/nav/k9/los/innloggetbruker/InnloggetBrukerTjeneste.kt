package no.nav.k9.los.innloggetbruker

import kotlinx.coroutines.CancellationException
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.postgresql.util.PSQLException
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDateTime

class InnloggetBrukerTjeneste(
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val azureGraphService: IAzureGraphService,
    private val pepClient: IPepClient,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(InnloggetBrukerTjeneste::class.java)

    suspend fun hentInnloggetBruker(token: IIdToken, kode6: Boolean, område: Områder): InnloggetBrukerDtoNy {
        val saksbehandler = finnOgVedlikehold(token, kode6)
        return InnloggetBrukerDtoNy(
            token.getUsername(),
            token.getName(),
            token.getNavIdent(),
            pepClient.tilganger(område),
            id = saksbehandler?.id,
            finnesISaksbehandlerTabell = saksbehandler != null
        )
    }

    private fun finnSaksbehandler(navIdent: String, epost: String, kode6: Boolean): Saksbehandler? =
        saksbehandlerRepository.finnSaksbehandlerMedIdent(navIdent, kode6)
            ?: saksbehandlerRepository.finnSaksbehandlerMedEpost(epost, kode6)

    suspend fun finnOgVedlikehold(token: IIdToken, kode6: Boolean): Saksbehandler? {
        val saksbehandler = finnSaksbehandler(token.getNavIdent(), token.getUsername(), kode6)
        if (saksbehandler == null) {
            log.info("Innlogget saksbehandler finnes ikke i saksbehandlertabellen og kan derfor ikke vedlikeholdes")
        } else {
            vedlikeholdHvisUtdatert(saksbehandler, token.getNavIdent(), token.getName(), token.getUsername(), kode6)
        }
        return saksbehandler
    }

    suspend fun vedlikeholdHvisUtdatert(
        saksbehandler: Saksbehandler,
        navident: String,
        navn: String,
        epost: String,
        skjermet: Boolean,
    ) {
        val nå = LocalDateTime.now(clock)
        val sistOppdatert = saksbehandler.sistOppdatert
        if (sistOppdatert != null && !sistOppdatert.isBefore(nå.minusHours(24))) {
            return
        }

        val enhet = try {
            azureGraphService.hentEnhetForInnloggetBruker()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Kunne ikke hente enhet for innlogget saksbehandler. Forsøker igjen ved neste innlogging", e)
            return
        }

        try {
            saksbehandlerRepository.vedlikeholdSaksbehandler(
                Saksbehandler(
                    id = saksbehandler.id,
                    navident = navident,
                    navn = navn,
                    epost = epost,
                    enhet = enhet,
                    områder = saksbehandler.områder,
                    skjermet = skjermet,
                    sistOppdatert = nå,
                )
            )
        } catch (e: PSQLException) {
            if (e.sqlState != "23505" || e.serverErrorMessage?.constraint != "saksbehandler_epost_key") {
                throw e
            }
            log.warn(
                "E-postkonflikt ved vedlikehold av saksbehandler med id={}. Krever manuell opprydding. Innlogging fortsetter uten vedlikehold",
                saksbehandler.id
            )
        }
    }
}
