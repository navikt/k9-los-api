package no.nav.k9.los.infrastruktur.brukerkontekst

import no.nav.k9.los.infrastruktur.abac.tilganger.OmrådeTilganger
import no.nav.k9.los.infrastruktur.idtoken.IdToken
import no.nav.k9.los.infrastruktur.idtoken.IdTokenLocal
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

internal object TestKontekstFactory {

    val ALLE_TILGANGER = OmrådeTilganger(
        harBasisTilgang = true,
        harTilgangTilKode6 = false, // matcher lokal oppførsel: kode6 er av lokalt
        erOppgavestyrer = true,
        harTilgangTilReserveringAvOppgaver = true,
        kanLeggeUtDriftsmelding = true,
    )

    val INGEN_TILGANGER = OmrådeTilganger(
        harBasisTilgang = false,
        harTilgangTilKode6 = false,
        erOppgavestyrer = false,
        harTilgangTilReserveringAvOppgaver = false,
        kanLeggeUtDriftsmelding = false,
    )

    fun brukerkontekst(
        område: Områder,
        idToken: IdToken = IdTokenLocal(),
        tilganger: OmrådeTilganger = ALLE_TILGANGER,
    ): BrukerkontekstMedOmråde = BrukerkontekstMedOmråde(
        område = område,
        navIdent = idToken.getNavIdent(),
        idToken = idToken,
        harBasisTilgang = tilganger.harBasisTilgang,
        harTilgangTilKode6 = tilganger.harTilgangTilKode6,
        erOppgavestyrer = tilganger.erOppgavestyrer,
        harTilgangTilReserveringAvOppgaver = tilganger.harTilgangTilReserveringAvOppgaver,
        harDriftstilgang = tilganger.kanLeggeUtDriftsmelding,
    )

    fun brukerkontekstUtenOmråde(
        idToken: IdToken = IdTokenLocal(),
        tilgangerPerOmråde: Map<Områder, OmrådeTilganger> = Områder.entries.associateWith { ALLE_TILGANGER },
    ): BrukerkontekstUtenOmråde = BrukerkontekstUtenOmråde(
        navIdent = idToken.getNavIdent(),
        idToken = idToken,
        harBasisTilgangIEttEllerFlereOmråder = tilgangerPerOmråde.values.any { it.harBasisTilgang },
        harKode6TilgangIEttEllerFlereOmråder = tilgangerPerOmråde.values.any { it.harTilgangTilKode6 },
        harDriftstilgangIEttEllerFlereOmråder = tilgangerPerOmråde.values.any { it.kanLeggeUtDriftsmelding },
    )
}
