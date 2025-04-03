package no.nav.k9.los.nyoppgavestyring.søkeboks

import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.integrasjon.abac.Action
import no.nav.k9.los.integrasjon.abac.Auditlogging
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.pdl.IPdlService
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EnkelOrderFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepositoryTxWrapper

class SøkeboksTjeneste(
    private val queryService: OppgaveQueryService,
    private val oppgaveRepository: OppgaveRepositoryTxWrapper,
    private val pdlService: IPdlService,
    private val pepClient: IPepClient,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste,
    private val saksbehandlerRepository: SaksbehandlerRepository,
) {
    suspend fun finnOppgaver(søkeord: String, oppgavestatus: List<Oppgavestatus>): List<SøkeboksOppgaveDto> {
        val oppgaver = when (søkeord.length) {
            11 -> {
                finnOppgaverForSøkersFnr(søkeord, oppgavestatus)
            }

            9 -> {
                finnOppgaverForJournalpostId(søkeord, oppgavestatus)
            }

            else -> {
                finnOppgaverForSaksnummer(søkeord, oppgavestatus)
            }
        }

        return oppgaver
            .filter { pepClient.harTilgangTilOppgaveV3(it, Action.read, Auditlogging.LOGG_VED_PERMIT) }
            .map {
                val aktorIdFraOppgave = it.hentVerdi("aktorId")
                val aktorId = when (aktorIdFraOppgave?.length) {
                    // For noen punsj-eventer er det feilaktig lagt inn fnr i feltet for 'aktorId', så hvis lengden er 11 gjøres et ekstra kall til PDL for å finne riktig aktør-id
                    11 -> pdlService.identifikator(aktorIdFraOppgave).aktorId?.data?.hentIdenter?.identer?.get(0)?.ident
                    13 -> aktorIdFraOppgave
                    else -> null
                }
                val person = if (aktorId != null) pdlService.person(aktorId).person else null
                val reservasjon = reservasjonV3Tjeneste.finnAktivReservasjon(it.reservasjonsnøkkel)
                val reservertAvSaksbehandler =
                    if (reservasjon != null) saksbehandlerRepository.finnSaksbehandlerMedId(reservasjon.reservertAv) else null
                SøkeboksOppgaveDto(it, person, reservasjon, reservertAvSaksbehandler)
            }
    }

    private fun finnOppgaverForJournalpostId(journalpostId: String, oppgavestatus: List<Oppgavestatus>): List<Oppgave> {
        val query = OppgaveQuery(
            filtere = listOf(
                FeltverdiOppgavefilter(
                    område = null,
                    kode = "oppgavestatus",
                    operator = EksternFeltverdiOperator.IN,
                    verdi = oppgavestatus
                ),
                FeltverdiOppgavefilter(
                    område = "K9",
                    kode = "journalpostId",
                    operator = EksternFeltverdiOperator.EQUALS,
                    verdi = listOf(journalpostId)
                )
            ),
            order = listOf(EnkelOrderFelt("K9", "mottattDato", true))
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
                ),
                FeltverdiOppgavefilter(
                    område = "K9",
                    kode = "aktorId",
                    operator = EksternFeltverdiOperator.IN,
                    verdi = listOf(aktørId, fnr)
                )
            ),
            order = listOf(EnkelOrderFelt("K9", "mottattDato", true))
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
                ),
                FeltverdiOppgavefilter(
                    område = "K9",
                    kode = "saksnummer",
                    operator = EksternFeltverdiOperator.EQUALS,
                    verdi = listOf(saksnummer)
                )
            ),
            order = listOf(EnkelOrderFelt("K9", "mottattDato", true))
        )
        return queryService.queryForOppgave(QueryRequest(oppgaveQuery = query))
    }
}