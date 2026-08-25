package no.nav.k9.los.infrastruktur.brukerkontekst

import no.nav.k9.los.infrastruktur.abac.Gruppeoppsett
import no.nav.k9.los.infrastruktur.idtoken.IdToken
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import java.util.*

internal class BrukerkontekstFactory(
    private val gruppeoppsett: Gruppeoppsett,
    private val lokaleTilganger: Boolean = false,
) {
    fun medOmråde(område: Områder, idToken: IdToken): BrukerkontekstMedOmråde {
        val grupperForOmråde = gruppeoppsett.forOmråde(område)
        val grupper = idToken.groups.map(UUID::fromString).toSet()
        return BrukerkontekstMedOmråde(
            område = område,
            navIdent = idToken.getNavIdent(),
            grupper = grupper,
            harBasisTilgang = lokaleTilganger || grupperForOmråde.girBasisTilgang(grupper),
            harTilgangTilKode6 = !lokaleTilganger && grupperForOmråde.kode6 in grupper,
            erOppgavestyrer = lokaleTilganger || grupperForOmråde.oppgavestyrer in grupper,
            harTilgangTilReserveringAvOppgaver = lokaleTilganger || grupperForOmråde.girReserveringstilgang(grupper),
            kanLeggeUtDriftsmelding = lokaleTilganger || gruppeoppsett.drift in grupper,
            idToken = idToken,
        )
    }

    fun utenOmråde(idToken: IdToken): BrukerkontekstUtenOmråde {
        val grupper = idToken.groups.map(UUID::fromString).toSet()
        return BrukerkontekstUtenOmråde(
            navIdent = idToken.getNavIdent(),
            grupper = grupper,
            harBasisTilgangIEttEllerFlereOmråder = lokaleTilganger || Områder.entries.any {
                gruppeoppsett.forOmråde(it).girBasisTilgang(grupper)
            },
            harKode6TilgangIEttEllerFlereOmråder = !lokaleTilganger && Områder.entries.any {
                gruppeoppsett.forOmråde(it).kode6 in grupper
            },
            kanLeggeUtDriftsmelding = lokaleTilganger || gruppeoppsett.drift in grupper,
            idToken = idToken,
        )
    }
}
