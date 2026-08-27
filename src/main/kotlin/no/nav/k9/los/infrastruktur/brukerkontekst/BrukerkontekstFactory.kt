package no.nav.k9.los.infrastruktur.brukerkontekst

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import no.nav.k9.los.infrastruktur.abac.tilganger.SifAbacPdpTilgangerKlient
import no.nav.k9.los.infrastruktur.idtoken.IdToken
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

internal class BrukerkontekstFactory(
    private val tilgangerKlient: SifAbacPdpTilgangerKlient? = null,
    private val lokaleTilganger: Boolean = false,
) {
    init {
        check(lokaleTilganger || tilgangerKlient != null) {
            "SifAbacPdpTilgangerKlient må være satt når lokaleTilganger er false"
        }
    }

    suspend fun medOmråde(område: Områder, idToken: IdToken): BrukerkontekstMedOmråde {
        if (lokaleTilganger) {
            return BrukerkontekstMedOmråde(
                område = område,
                navIdent = idToken.getNavIdent(),
                harBasisTilgang = true,
                harTilgangTilKode6 = false,
                erOppgavestyrer = true,
                harTilgangTilReserveringAvOppgaver = true,
                kanLeggeUtDriftsmelding = true,
                idToken = idToken,
            )
        }
        val tilganger = tilgangerKlient!!.tilganger(område, idToken)
        return BrukerkontekstMedOmråde(
            område = område,
            navIdent = idToken.getNavIdent(),
            harBasisTilgang = tilganger.harBasisTilgang,
            harTilgangTilKode6 = tilganger.harTilgangTilKode6,
            erOppgavestyrer = tilganger.erOppgavestyrer,
            harTilgangTilReserveringAvOppgaver = tilganger.harTilgangTilReserveringAvOppgaver,
            kanLeggeUtDriftsmelding = tilganger.kanLeggeUtDriftsmelding,
            idToken = idToken,
        )
    }

    suspend fun utenOmråde(idToken: IdToken): BrukerkontekstUtenOmråde {
        if (lokaleTilganger) {
            return BrukerkontekstUtenOmråde(
                navIdent = idToken.getNavIdent(),
                harBasisTilgangIEttEllerFlereOmråder = true,
                harKode6TilgangIEttEllerFlereOmråder = false, // kode6 er av lokalt, jf. medOmråde
                kanLeggeUtDriftsmelding = true,
                idToken = idToken,
            )
        }
        // Henter tilganger for alle områder parallelt. Hvert oppslag er cachet per område,
        // så etterfølgende medOmråde-kall treffer cachen.
        val tilgangerPerOmråde = coroutineScope {
            Områder.entries.map { område ->
                async { område to tilgangerKlient!!.tilganger(område, idToken) }
            }.map { it.await() }
        }
        return BrukerkontekstUtenOmråde(
            navIdent = idToken.getNavIdent(),
            harBasisTilgangIEttEllerFlereOmråder = tilgangerPerOmråde.any { it.second.harBasisTilgang },
            harKode6TilgangIEttEllerFlereOmråder = tilgangerPerOmråde.any { it.second.harTilgangTilKode6 },
            kanLeggeUtDriftsmelding = tilgangerPerOmråde.any { it.second.kanLeggeUtDriftsmelding },
            idToken = idToken,
        )
    }
}
