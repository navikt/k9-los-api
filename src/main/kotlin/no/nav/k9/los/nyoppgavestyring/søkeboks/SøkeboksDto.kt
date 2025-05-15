package no.nav.k9.los.nyoppgavestyring.søkeboks

import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import java.time.LocalDate
import java.time.LocalDateTime

enum class SøkeresultatType {
    IKKE_TILGANG,
    TOMT_RESULTAT,
    UTEN_PERSON,
    MED_PERSON,
}

sealed class Søkeresultat(val resultat: SøkeresultatType) {
    data object SøkeresultatIkkeTilgang : Søkeresultat(SøkeresultatType.IKKE_TILGANG)

    data object SøkeresultatTomtResultat : Søkeresultat(SøkeresultatType.TOMT_RESULTAT)

    data class SøkeresultatUtenPerson(
        val oppgaver: List<SøkeresultatOppgaveDto>,
    ) : Søkeresultat(SøkeresultatType.UTEN_PERSON)

    data class SøkeresultatMedPerson(
        val person: SøkeresultatPersonDto,
        val oppgaver: List<SøkeresultatOppgaveDto>,
    ) : Søkeresultat(SøkeresultatType.MED_PERSON)
}

data class SøkeresultatPersonDto(
    val navn: String,
    val fnr: String,
    val kjønn: String,
    val dødsdato: LocalDate?,
)

data class SøkeresultatOppgaveDto(
    val oppgaveNøkkel: OppgaveNøkkelDto,
    val ytelsestype: FagsakYtelseType,
    val behandlingstype: BehandlingType,
    val saksnummer: String?,
    val hastesak: Boolean,
    val journalpostId: String?,
    val opprettetTidspunkt: LocalDateTime?,
    val oppgavestatus: OppgavestatusMedNavn,
    val behandlingsstatus: BehandlingStatus?,
    val oppgavebehandlingsUrl: String?,
    val reservasjonsnøkkel: String,
    val reservertAvSaksbehandlerNavn: String?,
    val reservertAvSaksbehandlerIdent: String?,
    val reservertTom: LocalDateTime?,
)
