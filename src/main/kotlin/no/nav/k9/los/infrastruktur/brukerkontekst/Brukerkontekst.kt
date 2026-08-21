package no.nav.k9.los.infrastruktur.brukerkontekst

import no.nav.k9.los.infrastruktur.idtoken.IdToken
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import java.util.UUID

@ConsistentCopyVisibility // For å ikke omgå konstruktørens synlighet via copy-metode
data class BrukerkontekstMedOmråde internal constructor(
    val område: Områder,
    val navIdent: String,
    val grupper: Set<UUID>,
    val idToken: IdToken,
    val harBasisTilgang: Boolean,
    val harTilgangTilKode6: Boolean,
    val erOppgavestyrer: Boolean,
    val harTilgangTilReserveringAvOppgaver: Boolean,
    val kanLeggeUtDriftsmelding: Boolean,
)

@ConsistentCopyVisibility
data class BrukerkontekstUtenOmråde internal constructor(
    val navIdent: String,
    val grupper: Set<UUID>,
    val idToken: IdToken,
    val harBasisTilgangIEttEllerFlereOmråder: Boolean,
    val harKode6TilgangIEttEllerFlereOmråder: Boolean,
    val kanLeggeUtDriftsmelding: Boolean,
)
