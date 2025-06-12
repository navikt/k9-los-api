package no.nav.k9.los.nyoppgavestyring.søkeboks

import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.*
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import java.time.LocalDate
import java.time.LocalDateTime

enum class SøkeresultatType {
    IKKE_TILGANG,
    TOMT_RESULTAT,
    MED_RESULTAT,
}

sealed class Søkeresultat(val type: SøkeresultatType) {
    data object IkkeTilgang : Søkeresultat(SøkeresultatType.IKKE_TILGANG)

    data object TomtResultat : Søkeresultat(SøkeresultatType.TOMT_RESULTAT)

    data class MedResultat(
        val person: SøkeresultatPersonDto,
        val oppgaver: List<SøkeresultatOppgaveDto>
    ) : Søkeresultat(SøkeresultatType.MED_RESULTAT)
}

data class SøkeresultatPersonDto(
    val navn: String,
    val fnr: String,
    val kjønn: String,
    val dødsdato: LocalDate?,
) {
    constructor(personPdl: PersonPdlResponse) : this(
        navn = personPdl.person?.navn() ?: "Uten navn",
        fnr = personPdl.person?.fnr() ?: "Ukjent fnummer",
        kjønn = personPdl.person?.kjoenn() ?: "",
        dødsdato = personPdl.person?.doedsdato(),
    )
}

data class SøkeresultatOppgaveDto(
    val navn: String,
    val oppgaveNøkkel: OppgaveNøkkelDto,
    val ytelsestype: String,
    val saksnummer: String?,
    val hastesak: Boolean,
    val journalpostId: String?,
    val opprettetTidspunkt: LocalDateTime?,
    val status: String,
    val oppgavebehandlingsUrl: String?,
    val reservasjonsnøkkel: String,
    val reservertAvSaksbehandlerNavn: String?,
    val reservertAvSaksbehandlerIdent: String?,
    val reservertTom: LocalDateTime?,
    val fagsakÅr: Int?,
)
