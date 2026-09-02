package no.nav.k9.los.infrastruktur.brukerkontekst

import no.nav.k9.los.infrastruktur.idtoken.IdToken
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

@ConsistentCopyVisibility // For å ikke omgå konstruktørens synlighet via copy-metode
data class BrukerkontekstMedOmråde internal constructor(
    val område: Områder,
    val navIdent: String,
    val idToken: IdToken,
    val harBasisTilgang: Boolean,
    val harTilgangTilKode6: Boolean,
    val erOppgavestyrer: Boolean,
    val harTilgangTilReserveringAvOppgaver: Boolean,
    val harDriftstilgang: Boolean,
) {
    fun krevOmråde(område: Områder) = require(this.område == område) {
        "Krever at handlingen for område $område er samme som området på brukerkontekst ${this.område}"
    }
}

@ConsistentCopyVisibility
data class BrukerkontekstUtenOmråde internal constructor(
    val navIdent: String,
    val idToken: IdToken,
    val områderMedBasisTilgang: List<Områder>,
    val harBasisTilgangIEttEllerFlereOmråder: Boolean,
    val harKode6TilgangIEttEllerFlereOmråder: Boolean,
    val erOppgavestyrerIEttEllerFlereOmråder: Boolean,
    val harTilgangTilReserveringAvOppgaverIEttEllerFlereOmråder: Boolean,
    val harDriftstilgangIEttEllerFlereOmråder: Boolean,
)
