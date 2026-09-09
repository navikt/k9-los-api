package no.nav.k9.los.innloggetbruker

import kotlinx.coroutines.CancellationException
import no.nav.k9.los.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.postgresql.util.PSQLException
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDateTime

class InnloggetBrukerTjeneste(
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val azureGraphService: IAzureGraphService,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(InnloggetBrukerTjeneste::class.java)

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
