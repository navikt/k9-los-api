package no.nav.k9.los.infrastruktur.brukerkontekst

import no.nav.k9.los.infrastruktur.abac.Gruppeoppsett
import no.nav.k9.los.infrastruktur.abac.tilganger.PdpTilgangsskygge
import no.nav.k9.los.infrastruktur.abac.tilganger.Tilganger
import no.nav.k9.los.infrastruktur.idtoken.IdToken
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import java.util.UUID

internal class BrukerkontekstFactory(
    private val gruppeoppsett: Gruppeoppsett,
    private val pdpTilgangsskygge: PdpTilgangsskygge,
    private val lokaleTilganger: Boolean = false,
) {
    suspend fun medOmråde(område: Områder, idToken: IdToken): BrukerkontekstMedOmråde {
        val grupperForOmråde = gruppeoppsett.forOmråde(område)
        val grupper = idToken.groups.map(UUID::fromString).toSet()
        val autoritative = Tilganger(
            basis = lokaleTilganger || grupperForOmråde.girBasisTilgang(grupper),
            kode6 = !lokaleTilganger && grupperForOmråde.kode6 in grupper,
            oppgavestyring = lokaleTilganger || grupperForOmråde.oppgavestyrer in grupper,
            reservering = lokaleTilganger || grupperForOmråde.girReserveringstilgang(grupper),
            drift = lokaleTilganger || gruppeoppsett.drift in grupper,
        )
        // PDP observerer foreløpig bare K9. Gruppe-claims er fortsatt autoritativ fasit.
        if (område == Områder.K9) {
            pdpTilgangsskygge.observer(idToken, autoritative)
        }
        return BrukerkontekstMedOmråde(
            område = område,
            navIdent = idToken.getNavIdent(),
            grupper = grupper,
            harBasisTilgang = autoritative.basis,
            harTilgangTilKode6 = autoritative.kode6,
            erOppgavestyrer = autoritative.oppgavestyring,
            harTilgangTilReserveringAvOppgaver = autoritative.reservering,
            harDriftstilgang = autoritative.drift,
            idToken = idToken,
        )
    }

    suspend fun utenOmråde(idToken: IdToken): BrukerkontekstUtenOmråde {
        val grupper = idToken.groups.map(UUID::fromString).toSet()
        val kontekster = Områder.entries.associateWith { medOmråde(it, idToken) }
        return BrukerkontekstUtenOmråde(
            navIdent = idToken.getNavIdent(),
            grupper = grupper,
            områderMedBasisTilgang = kontekster.filterValues { it.harBasisTilgang }.keys.toList(),
            harBasisTilgangIEttEllerFlereOmråder = kontekster.values.any { it.harBasisTilgang },
            harKode6TilgangIEttEllerFlereOmråder = kontekster.values.any { it.harTilgangTilKode6 },
            erOppgavestyrerIEttEllerFlereOmråder = kontekster.values.any { it.erOppgavestyrer },
            harTilgangTilReserveringAvOppgaverIEttEllerFlereOmråder = kontekster.values.any {
                it.harTilgangTilReserveringAvOppgaver
            },
            harDriftstilgangIEttEllerFlereOmråder = kontekster.values.any { it.harDriftstilgang },
            idToken = idToken,
        )
    }
}
