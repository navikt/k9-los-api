package no.nav.k9.los.infrastruktur.brukerkontekst

import no.nav.k9.los.infrastruktur.abac.SifAbacPdpKlienter
import no.nav.k9.los.infrastruktur.abac.tilganger.Tilganger
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

internal class BrukerkontekstFactory(
    private val sifAbacPdpKlienter: SifAbacPdpKlienter,
) {
    suspend fun medOmråde(område: Områder, idToken: IIdToken): BrukerkontekstMedOmråde {
        return medOmråde(område, idToken, sifAbacPdpKlienter.forOmråde(område).hentTilganger(idToken))
    }

    private fun medOmråde(område: Områder, idToken: IIdToken, autoritative: Tilganger): BrukerkontekstMedOmråde {
        return BrukerkontekstMedOmråde(
            område = område,
            navIdent = idToken.getNavIdent(),
            harBasisTilgang = autoritative.basis,
            harTilgangTilKode6 = autoritative.kode6,
            erOppgavestyrer = autoritative.oppgavestyring,
            harTilgangTilReserveringAvOppgaver = autoritative.reservering,
            harDriftstilgang = autoritative.drift,
            idToken = idToken,
        )
    }

    suspend fun utenOmråde(idToken: IIdToken): BrukerkontekstUtenOmråde {
        val kontekster = Områder.entries.associateWith { medOmråde(it, idToken) }
        return BrukerkontekstUtenOmråde(
            navIdent = idToken.getNavIdent(),
            områderMedBasisTilgang = kontekster.filterValues { it.harBasisTilgang }.keys.toList(),
            harBasisTilgangIEttEllerFlereOmråder = kontekster.values.any { it.harBasisTilgang },
            // Kode6 er global i PDP; bruk én verdi uten å sammenligne uavhengig cachede svar.
            harKode6TilgangIEttEllerFlereOmråder = kontekster.getValue(Områder.K9).harTilgangTilKode6,
            erOppgavestyrerIEttEllerFlereOmråder = kontekster.values.any { it.erOppgavestyrer },
            harTilgangTilReserveringAvOppgaverIEttEllerFlereOmråder = kontekster.values.any {
                it.harTilgangTilReserveringAvOppgaver
            },
            harDriftstilgangIEttEllerFlereOmråder = kontekster.values.any { it.harDriftstilgang },
            idToken = idToken,
        )
    }
}
