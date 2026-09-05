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
                harDriftstilgang = true,
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
            harDriftstilgang = tilganger.kanLeggeUtDriftsmelding,
            idToken = idToken,
        )
    }

    suspend fun utenOmråde(idToken: IdToken): BrukerkontekstUtenOmråde {
        if (lokaleTilganger) {
            return BrukerkontekstUtenOmråde(
                navIdent = idToken.getNavIdent(),
                områderMedBasisTilgang = Områder.entries,
                harBasisTilgangIEttEllerFlereOmråder = true,
                harKode6TilgangIEttEllerFlereOmråder = false, // kode6 er av lokalt, jf. medOmråde
                erOppgavestyrerIEttEllerFlereOmråder = true,
                harTilgangTilReserveringAvOppgaverIEttEllerFlereOmråder = true,
                harDriftstilgangIEttEllerFlereOmråder = true,
                idToken = idToken,
            )
        }
        // Henter tilganger for alle områder parallelt. Hvert oppslag er cachet per område,
        // så etterfølgende medOmråde-kall treffer cachen.
        val tilgangerPerOmråde = coroutineScope {
            Områder.entries.map { område ->
                område to async { tilgangerKlient!!.tilganger(område, idToken) }
            }.associate { (område, tilganger) -> område to tilganger.await() }
        }
        return BrukerkontekstUtenOmråde(
            navIdent = idToken.getNavIdent(),
            områderMedBasisTilgang = tilgangerPerOmråde.filterValues { it.harBasisTilgang }.keys.toList(),
            harBasisTilgangIEttEllerFlereOmråder = tilgangerPerOmråde.values.any { it.harBasisTilgang },
            harKode6TilgangIEttEllerFlereOmråder = tilgangerPerOmråde.values.any { it.harTilgangTilKode6 },
            erOppgavestyrerIEttEllerFlereOmråder = tilgangerPerOmråde.values.any { it.erOppgavestyrer },
            harTilgangTilReserveringAvOppgaverIEttEllerFlereOmråder = tilgangerPerOmråde.values.any {
                it.harTilgangTilReserveringAvOppgaver
            },
            harDriftstilgangIEttEllerFlereOmråder = tilgangerPerOmråde.values.any { it.kanLeggeUtDriftsmelding },
            idToken = idToken,
        )
    }
}
