package no.nav.k9.los.søkeboks

import no.nav.k9.los.infrastruktur.pdl.*
import no.nav.k9.los.kodeverk.FagsakYtelseType
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto
import no.nav.k9.los.oppgaveuthenting.sammendrag.OppgaveSammendragDto
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
    constructor(person: PersonPdl?) : this(
        navn = person?.navn() ?: "Uten navn",
        fnr = person?.fnr() ?: "Ukjent fnummer",
        kjønn = person?.kjoenn() ?: "",
        dødsdato = person?.doedsdato(),
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
    val fagsakÅr: Int?,
) {
    /**
     * Denne DTO-en er en flatere visning av [OppgaveSammendragDto] og utledes fra den,
     * slik at feltkodene bare tolkes ett sted (i områdets OmrådeAdapter).
     */
    constructor(sammendrag: OppgaveSammendragDto) : this(
        navn = sammendrag.person?.navn ?: "Uten navn",
        oppgaveNøkkel = sammendrag.oppgaveNøkkel,
        ytelsestype = sammendrag.ytelse?.navn ?: FagsakYtelseType.UKJENT.navn,
        saksnummer = sammendrag.saksnummer,
        hastesak = sammendrag.hastesak,
        journalpostId = sammendrag.journalpostId,
        opprettetTidspunkt = sammendrag.opprettetTidspunkt,
        status = sammendrag.behandlingsstatus?.navn ?: sammendrag.oppgavestatus.navn,
        oppgavebehandlingsUrl = sammendrag.oppgavebehandlingsUrl,
        reservasjonsnøkkel = sammendrag.reservasjonsnøkkel,
        fagsakÅr = sammendrag.fagsakÅr,
    )
}
