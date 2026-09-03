package no.nav.k9.los.infrastruktur.abac.tilganger

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import org.slf4j.LoggerFactory

internal interface PdpTilgangsskygge {
    suspend fun observerOgReturnerAutoritative(
        idToken: IIdToken,
        hentAutoritative: suspend () -> Tilganger,
    ): Tilganger
}

internal class SifAbacPdpTilgangsskygge(
    private val klient: TilgangerKlient,
) : PdpTilgangsskygge {
    private val log = LoggerFactory.getLogger(SifAbacPdpTilgangsskygge::class.java)

    override suspend fun observerOgReturnerAutoritative(
        idToken: IIdToken,
        hentAutoritative: suspend () -> Tilganger,
    ): Tilganger = coroutineScope {
        val pdpDeferred = async {
            try {
                klient.hent(idToken)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PdpResultat.Feil(e::class.simpleName ?: "UkjentFeil")
            }
        }
        val autoritativeDeferred = async { hentAutoritative() }
        val autoritativeResultat = autoritativeDeferred.await()

        // Kun skyggegrenen er fail-open. Feil i dagens autoritative beregning skal fortsatt propagere.
        when (val pdpResultat = pdpDeferred.await()) {
            is PdpResultat.Feil -> log.info(
                "Skyggekall mot sif-abac-pdp feilet: feiltype={}, status={}",
                pdpResultat.type,
                pdpResultat.status?.toString() ?: "ikke_tilgjengelig",
            )

            is PdpResultat.Suksess -> {
                val avvik = finnAvvik(autoritativeResultat, pdpResultat)
                if (avvik.isEmpty()) {
                    log.info("Ingen avvik i skyggetilganger fra sif-abac-pdp")
                } else {
                    log.info("Avvik i skyggetilganger fra sif-abac-pdp: tilgangstyper={}", avvik.joinToString(","))
                }
            }
        }
        autoritativeResultat
    }

    internal fun finnAvvik(autoritative: Tilganger, resultat: PdpResultat.Suksess): Set<Tilgangstype> = buildSet {
        val pdp = resultat.tilganger
        if (autoritative.basis != pdp.basis) add(Tilgangstype.BASIS)
        if (autoritative.kode6 != pdp.kode6) add(Tilgangstype.KODE6)
        if (autoritative.oppgavestyring != pdp.oppgavestyring) add(Tilgangstype.OPPGAVESTYRING)
        if (autoritative.reservering != pdp.reservering) add(Tilgangstype.RESERVERING)
        if (autoritative.drift != pdp.drift) add(Tilgangstype.DRIFT)
    }
}

internal object IngenPdpTilgangsskygge : PdpTilgangsskygge {
    override suspend fun observerOgReturnerAutoritative(
        idToken: IIdToken,
        hentAutoritative: suspend () -> Tilganger,
    ) = hentAutoritative()
}
