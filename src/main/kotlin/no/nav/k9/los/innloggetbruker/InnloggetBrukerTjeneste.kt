package no.nav.k9.los.innloggetbruker

import kotlinx.coroutines.CancellationException
import no.nav.k9.los.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
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
        navn: String
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

        saksbehandlerRepository.vedlikeholdSaksbehandler(
            Saksbehandler(
                id = saksbehandler.id,
                navident = navident,
                navn = navn,
                epost = saksbehandler.epost,
                enhet = enhet
            ),
            oppdatertTidspunkt = nå
        )
    }
}
