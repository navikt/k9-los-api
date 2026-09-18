package no.nav.k9.los.søkeboks

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.k9.los.oppgaveuthenting.sammendrag.OppgaveSammendragDto

@JsonSubTypes(
    JsonSubTypes.Type(value = SøkeresultatSammendrag.IkkeTilgang::class, name = "IKKE_TILGANG"),
    JsonSubTypes.Type(value = SøkeresultatSammendrag.TomtResultat::class, name = "TOMT_RESULTAT"),
    JsonSubTypes.Type(value = SøkeresultatSammendrag.MedResultat::class, name = "MED_RESULTAT"),
)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
)
sealed class SøkeresultatSammendrag(val type: SøkeresultatType) {
    data object IkkeTilgang : SøkeresultatSammendrag(SøkeresultatType.IKKE_TILGANG)

    data object TomtResultat : SøkeresultatSammendrag(SøkeresultatType.TOMT_RESULTAT)

    data class MedResultat(
        val oppgaver: List<OppgaveSammendragDto>,
    ) : SøkeresultatSammendrag(SøkeresultatType.MED_RESULTAT)
}
