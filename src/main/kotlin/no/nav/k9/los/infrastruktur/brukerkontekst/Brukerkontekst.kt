package no.nav.k9.los.infrastruktur.brukerkontekst

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

@ConsistentCopyVisibility // For å ikke omgå konstruktørens synlighet via copy-metode
data class BrukerkontekstMedOmråde internal constructor(
    val område: Områder,
    val bruker: InnloggetBruker,
    private val tilganger: Brukertilganger,
) {
    fun harBasisTilgang(): Boolean = tilganger.harBasisTilgang
    fun harTilgangTilKode6(): Boolean = tilganger.harTilgangTilKode6
    fun erOppgavestyrer(): Boolean = tilganger.erOppgavestyrer
    fun harTilgangTilReserveringAvOppgaver(): Boolean = tilganger.harTilgangTilReserveringAvOppgaver
    fun kanLeggeUtDriftsmelding(): Boolean = tilganger.kanLeggeUtDriftsmelding
}

@ConsistentCopyVisibility
data class BrukerkontekstUtenOmråde internal constructor(
    val bruker: InnloggetBruker,
    private val tilganger: BrukertilgangerUtenOmråde,
) {
    fun harBasisTilgangIEttEllerFlereOmråder(): Boolean = tilganger.harBasisTilgangIEttEllerFlereOmråder
    fun harKode6TilgangIEttEllerFlereOmråder(): Boolean = tilganger.harKode6TilgangIEttEllerFlereOmråder
    fun kanLeggeUtDriftsmelding(): Boolean = tilganger.kanLeggeUtDriftsmelding
}

internal data class Brukertilganger(
    val harBasisTilgang: Boolean,
    val harTilgangTilKode6: Boolean,
    val erOppgavestyrer: Boolean,
    val harTilgangTilReserveringAvOppgaver: Boolean,
    val kanLeggeUtDriftsmelding: Boolean,
)

internal data class BrukertilgangerUtenOmråde(
    val harBasisTilgangIEttEllerFlereOmråder: Boolean,
    val harKode6TilgangIEttEllerFlereOmråder: Boolean,
    val kanLeggeUtDriftsmelding: Boolean,
)
