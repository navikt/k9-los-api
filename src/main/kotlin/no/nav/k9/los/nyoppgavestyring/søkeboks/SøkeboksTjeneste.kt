package no.nav.k9.los.nyoppgavestyring.søkeboks

import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.IPdlService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.navn
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EnkelOrderFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepositoryTxWrapper
import java.time.LocalDateTime

class SøkeboksTjeneste(
    private val queryService: OppgaveQueryService,
    private val oppgaveRepository: OppgaveRepositoryTxWrapper,
    private val pdlService: IPdlService,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste,
    private val saksbehandlerRepository: SaksbehandlerRepository,
) {
    suspend fun finnOppgaver(søkeord: String, oppgavestatus: List<Oppgavestatus>): Søkeresultat {
        val oppgaver = when (søkeord.length) {
            11 -> {
                val pdlResponse = pdlService.identifikator(søkeord)
                if (pdlResponse.ikkeTilgang) return Søkeresultat.IkkeTilgang
                val aktørIder = pdlResponse.aktorId?.data?.hentIdenter?.identer?.map { it.ident } ?: emptyList()
                finnOppgaverForAktørId(aktørIder + søkeord, oppgavestatus)
            }

            9 -> {
                finnOppgaverForJournalpostId(søkeord, oppgavestatus)
            }

            else -> finnOppgaverForSaksnummer(søkeord, oppgavestatus)
        }

        if (oppgaver.isEmpty()) {
            return Søkeresultat.TomtResultat
        }


        /**
         * Starter med en flat liste av oppgaver og må koble disse til persondata.
         * Transformasjonen er kompleks fordi:
         * - Ønsker å unngå å kalle PDL for hver oppgave
         * - Oppgavene kan inneholde flere aktørIder
         * - Forskjellige aktørId kan gi samme person (kombinasjon av navn/fnr/kjønn/dødsdato)
         * Resultatet blir normalisert til en struktur som er enkel for frontend å håndtere
         */
        val personerOgOppgaver = oppgaver
            .groupBy { oppgave -> oppgave.hentVerdi("aktorId") }
            .map { (aktørId, oppgaverForPerson) ->
                aktørId?.let {
                    val personPdlResponse = pdlService.person(it)
                    if (personPdlResponse.ikkeTilgang) return Søkeresultat.IkkeTilgang
                    personPdlResponse.person?.let { personPdl ->
                        SøkeresultatPersonDto(personPdl) to oppgaverForPerson.tilDto(personPdl.navn())
                    }
                } ?: (null to oppgaverForPerson.tilDto("Uten navn"))
            }
            .groupBy { (person) -> person }
            .mapValues { (_, pairs) -> pairs.flatMap { (_, oppgaver) -> oppgaver } }
        return Søkeresultat.MedResultat(
            person = personerOgOppgaver.keys.singleOrNull(),
            oppgaver = personerOgOppgaver.values.flatten()
        )
    }

    private fun List<Oppgave>.tilDto(navn: String): List<SøkeresultatOppgaveDto> {
        return this.map { oppgave ->
            val reservasjon = reservasjonV3Tjeneste.finnAktivReservasjon(oppgave.reservasjonsnøkkel)
            val reservertAv = if (reservasjon != null)
                saksbehandlerRepository.finnSaksbehandlerMedId(reservasjon.reservertAv) else null

            SøkeresultatOppgaveDto(
                navn = navn,
                oppgaveNøkkel = OppgaveNøkkelDto(oppgave),
                ytelsestype = oppgave.hentVerdi("ytelsestype")?.let { FagsakYtelseType.fraKode(it).navn }
                    ?: FagsakYtelseType.UKJENT.navn,
                saksnummer = oppgave.hentVerdi("saksnummer"),
                hastesak = oppgave.hentVerdi("hastesak") == "true",
                journalpostId = oppgave.hentVerdi("journalpostId"),
                opprettetTidspunkt = oppgave.hentVerdi("registrertDato")?.let { dato -> LocalDateTime.parse(dato) },
                status = oppgave.hentVerdi("behandlingsstatus")
                    ?.let { kode -> BehandlingStatus.fraKode(kode).navn }
                    ?: Oppgavestatus.fraKode(oppgave.status).visningsnavn,
                oppgavebehandlingsUrl = oppgave.getOppgaveBehandlingsurl(),
                reservasjonsnøkkel = oppgave.reservasjonsnøkkel,
                reservertAvSaksbehandlerNavn = reservertAv?.navn,
                reservertAvSaksbehandlerIdent = reservertAv?.brukerIdent,
                reservertTom = reservasjon?.gyldigTil,
            )
        }
    }

    private fun finnOppgaverForJournalpostId(
        journalpostId: String,
        oppgavestatus: List<Oppgavestatus>
    ): List<Oppgave> {
        val query = OppgaveQuery(
            filtere = listOf(
                FeltverdiOppgavefilter(
                    område = null,
                    kode = "oppgavestatus",
                    operator = EksternFeltverdiOperator.IN,
                    verdi = oppgavestatus
                ), FeltverdiOppgavefilter(
                    område = "K9",
                    kode = "journalpostId",
                    operator = EksternFeltverdiOperator.EQUALS,
                    verdi = listOf(journalpostId)
                )
            ), order = listOf(EnkelOrderFelt("K9", "mottattDato", true))
        )
        return queryService.queryForOppgave(QueryRequest(oppgaveQuery = query))
    }

    private suspend fun finnOppgaverForSøkersFnr(fnr: String, oppgavestatus: List<Oppgavestatus>): List<Oppgave> {
        val aktørId =
            pdlService.identifikator(fnr).aktorId?.data?.hentIdenter?.identer?.get(0)?.ident ?: return emptyList()
        val query = OppgaveQuery(
            filtere = listOf(
                FeltverdiOppgavefilter(
                    område = null,
                    kode = "oppgavestatus",
                    operator = EksternFeltverdiOperator.IN,
                    verdi = oppgavestatus
                ), FeltverdiOppgavefilter(
                    område = "K9",
                    kode = "aktorId",
                    operator = EksternFeltverdiOperator.IN,
                    verdi = listOf(aktørId, fnr)
                )
            ), order = listOf(EnkelOrderFelt("K9", "mottattDato", true))
        )
        return queryService.queryForOppgave(QueryRequest(oppgaveQuery = query))
    }

    private fun finnOppgaverForAktørId(aktørIder: List<String>, oppgavestatus: List<Oppgavestatus>): List<Oppgave> {
        val query = OppgaveQuery(
            filtere = listOf(
                FeltverdiOppgavefilter(
                    område = null,
                    kode = "oppgavestatus",
                    operator = EksternFeltverdiOperator.IN,
                    verdi = oppgavestatus
                ), FeltverdiOppgavefilter(
                    område = "K9",
                    kode = "aktorId",
                    operator = EksternFeltverdiOperator.IN,
                    verdi = aktørIder
                )
            ), order = listOf(EnkelOrderFelt("K9", "mottattDato", true))
        )
        return queryService.queryForOppgave(QueryRequest(oppgaveQuery = query))
    }

    private fun finnOppgaverForSaksnummer(saksnummer: String, oppgavestatus: List<Oppgavestatus>): List<Oppgave> {
        val query = OppgaveQuery(
            filtere = listOf(
                FeltverdiOppgavefilter(
                    område = null,
                    kode = "oppgavestatus",
                    operator = EksternFeltverdiOperator.IN,
                    verdi = oppgavestatus
                ), FeltverdiOppgavefilter(
                    område = "K9",
                    kode = "saksnummer",
                    operator = EksternFeltverdiOperator.EQUALS,
                    verdi = listOf(saksnummer)
                )
            ), order = listOf(EnkelOrderFelt("K9", "mottattDato", true))
        )
        return queryService.queryForOppgave(QueryRequest(oppgaveQuery = query))
    }
}