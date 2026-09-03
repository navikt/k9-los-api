package no.nav.k9.los.infrastruktur.abac.tilganger

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import no.nav.k9.los.infrastruktur.abac.ISifAbacPdpKlient
import no.nav.k9.los.infrastruktur.abac.SifAbacPdpHttpException
import no.nav.k9.los.infrastruktur.idtoken.IdToken
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class SifAbacPdpTilgangsskygge(
    private val klient: ISifAbacPdpKlient,
    private val skyggeScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineName("pdp-tilgangsskygge")
    ),
    private val timeout: Duration = 3.seconds,
) : PdpTilgangsskygge {
    private val log = LoggerFactory.getLogger(SifAbacPdpTilgangsskygge::class.java)

    override fun observer(idToken: IdToken, autoritative: Tilganger) {
        skyggeScope.launch {
            try {
                val fraPdp = withTimeout(timeout) { klient.hentTilganger(idToken) }
                val avvik = finnAvvik(autoritative, fraPdp)
                if (avvik.isNotEmpty()) {
                    log.warn("Avvik i skyggetilganger fra sif-abac-pdp: tilgangstyper={}", avvik.joinToString(","))
                }
            } catch (_: TimeoutCancellationException) {
                log.warn("Skyggekall mot sif-abac-pdp brukte mer enn {}", timeout)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn(
                    "Skyggekall mot sif-abac-pdp feilet: feiltype={}, status={}",
                    e::class.simpleName ?: "UkjentFeil",
                    (e as? SifAbacPdpHttpException)?.status?.toString() ?: "ikke_tilgjengelig",
                )
            }
        }
    }

    internal fun finnAvvik(autoritative: Tilganger, fraPdp: Tilganger): Set<Tilgangstype> = buildSet {
        if (autoritative.basis != fraPdp.basis) add(Tilgangstype.BASIS)
        if (autoritative.kode6 != fraPdp.kode6) add(Tilgangstype.KODE6)
        if (autoritative.oppgavestyring != fraPdp.oppgavestyring) add(Tilgangstype.OPPGAVESTYRING)
        if (autoritative.reservering != fraPdp.reservering) add(Tilgangstype.RESERVERING)
        if (autoritative.drift != fraPdp.drift) add(Tilgangstype.DRIFT)
    }
}
