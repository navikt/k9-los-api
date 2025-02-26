package no.nav.k9.los.nyoppgavestyring.søkeboks

import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.integrasjon.abac.Action
import no.nav.k9.los.integrasjon.abac.Auditlogging
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.pdl.IPdlService
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EnkelOrderFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
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
    suspend fun finnOppgaver(søkeord: String, fraAktiv: Boolean): List<SøkeboksOppgaveDto> {
        val oppgaver = when (søkeord.length) {
            11 -> {
                finnOppgaverForSøkersFnr(søkeord, fraAktiv)
            }

            9 -> {
                finnOppgaverForJournalpostId(søkeord, fraAktiv)
            }

            else -> {
                finnOppgaverForSaksnummer(søkeord, fraAktiv)
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

    private fun finnOppgaverForJournalpostId(journalpostId: String, fraAktiv: Boolean): List<Oppgave> {
        val query = OppgaveQuery(
            listOf(
                FeltverdiOppgavefilter(
                    område = "K9",
                    kode = "journalpostId",
                    operator = "EQUALS",
                    verdi = listOf(journalpostId)
                )
            )
        )
        val oppgaveEksternIder = queryService.queryForOppgaveEksternId(QueryRequest(oppgaveQuery = query, fraAktiv = fraAktiv))
        return oppgaveRepository.hentOppgaver(
            eksternoppgaveIder = oppgaveEksternIder,
        )
    }

    private suspend fun finnOppgaverForSøkersFnr(fnr: String, fraAktiv: Boolean): List<Oppgave> {
        val aktørId =
            pdlService.identifikator(fnr).aktorId?.data?.hentIdenter?.identer?.get(0)?.ident ?: return emptyList()
        val query = OppgaveQuery(
            filtere = listOf(
                FeltverdiOppgavefilter(
                    område = "K9",
                    kode = "aktorId",
                    operator = "IN",
                    verdi = listOf(aktørId, fnr)
                )
            )
        )
        val oppgaveEksternIder = queryService.queryForOppgaveEksternId(QueryRequest(oppgaveQuery = query, fraAktiv = fraAktiv))
        return oppgaveRepository.hentOppgaver(
            eksternoppgaveIder = oppgaveEksternIder,
        ).sortedBy { it.hentVerdi("mottattDato") }
    }

    private fun finnOppgaverForSaksnummer(saksnummer: String, fraAktiv: Boolean): List<Oppgave> {
        val query = OppgaveQuery(
            listOf(
                FeltverdiOppgavefilter(
                    område = "K9",
                    kode = "saksnummer",
                    operator = "EQUALS",
                    verdi = listOf(saksnummer)
                )
            )
        )
        val oppgaveEksternIder = queryService.queryForOppgaveEksternId(QueryRequest(oppgaveQuery = query, fraAktiv = fraAktiv))
        return oppgaveRepository.hentOppgaver(
            eksternoppgaveIder = oppgaveEksternIder,
        )
    }
}