package no.nav.k9.los.infrastruktur.brukerkontekst

import no.nav.k9.los.infrastruktur.abac.Gruppeoppsett
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import java.util.UUID

internal class Brukerkontekstfabrikk(
    private val gruppeoppsett: Gruppeoppsett,
    private val lokaleTilganger: Boolean = false,
) {
    fun medOmråde(område: Områder, bruker: InnloggetBruker): BrukerkontekstMedOmråde {
        val grupper = gruppeoppsett.forOmråde(område)
        return BrukerkontekstMedOmråde(
            område = område,
            bruker = bruker,
            tilganger = Brukertilganger(
                harBasisTilgang = lokaleTilganger || bruker.iGruppe(grupper.saksbehandler) || bruker.iGruppe(grupper.veileder),
                harTilgangTilKode6 = !lokaleTilganger && bruker.iGruppe(grupper.kode6),
                erOppgavestyrer = lokaleTilganger || bruker.iGruppe(grupper.oppgavestyrer),
                harTilgangTilReserveringAvOppgaver = lokaleTilganger || bruker.iGruppe(grupper.saksbehandler),
                kanLeggeUtDriftsmelding = lokaleTilganger || bruker.iGruppe(gruppeoppsett.drift),
            ),
        )
    }

    fun utenOmråde(bruker: InnloggetBruker): BrukerkontekstUtenOmråde = BrukerkontekstUtenOmråde(
        bruker = bruker,
        tilganger = BrukertilgangerUtenOmråde(
            harBasisTilgangIEttEllerFlereOmråder = lokaleTilganger || Områder.entries.any {
                val grupper = gruppeoppsett.forOmråde(it)
                bruker.iGruppe(grupper.saksbehandler) || bruker.iGruppe(grupper.veileder)
            },
            harKode6TilgangIEttEllerFlereOmråder = !lokaleTilganger && Områder.entries.any {
                bruker.iGruppe(gruppeoppsett.forOmråde(it).kode6)
            },
            kanLeggeUtDriftsmelding = lokaleTilganger || bruker.iGruppe(gruppeoppsett.drift),
        ),
    )

    private fun InnloggetBruker.iGruppe(gruppe: UUID?): Boolean = gruppe != null && gruppe in grupper
}
