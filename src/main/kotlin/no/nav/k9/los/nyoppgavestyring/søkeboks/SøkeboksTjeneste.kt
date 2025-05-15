package no.nav.k9.los.nyoppgavestyring.søkeboks

import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.Action
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.Auditlogging
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.*
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.fnr
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.kjoenn
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.navn
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EnkelOrderFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepositoryTxWrapper
import java.time.LocalDateTime

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

    suspend fun finnOppgaverNy(søkeord: String, oppgavestatus: List<Oppgavestatus>): Søkeresultat {
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

        if (oppgaver.any { !pepClient.harTilgangTilOppgaveV3(it, Action.read, Auditlogging.LOGG_VED_PERMIT) }) {
            return Søkeresultat.SøkeresultatIkkeTilgang
        }

        if (oppgaver.isEmpty()) {
            return Søkeresultat.SøkeresultatTomtResultat
        }

        val aktørIder = oppgaver.mapNotNullTo(HashSet()) { it.hentVerdi("aktorId") }

        val oppgaverDto: List<SøkeresultatOppgaveDto> = oppgaver.map {
            val reservasjon = reservasjonV3Tjeneste.finnAktivReservasjon(it.reservasjonsnøkkel)
            val reservertAv =
                if (reservasjon != null) saksbehandlerRepository.finnSaksbehandlerMedId(reservasjon.reservertAv) else null

            SøkeresultatOppgaveDto(
                ytelsestype = it.hentVerdi("ytelsestype")?.let { FagsakYtelseType.fraKode(it) }
                    ?: FagsakYtelseType.UKJENT,
                behandlingstype = BehandlingType.fraKode(it.hentVerdi("behandlingTypekode")!!),
                saksnummer = it.hentVerdi("saksnummer"),
                hastesak = it.hentVerdi("hastesak") == "true",
                oppgaveNøkkel = OppgaveNøkkelDto(it),
                journalpostId = it.hentVerdi("journalpostId"),
                opprettetTidspunkt = it.hentVerdi("registrertDato")?.let { LocalDateTime.parse(it) },
                oppgavestatus = OppgavestatusMedNavn.valueOf(it.status),
                behandlingsstatus = it.hentVerdi("behandlingsstatus")?.let { BehandlingStatus.fraKode(it) },
                oppgavebehandlingsUrl = it.getOppgaveBehandlingsurl(),
                reservasjonsnøkkel = it.reservasjonsnøkkel,
                reservertAvSaksbehandlerNavn = reservertAv?.navn,
                reservertAvSaksbehandlerIdent = reservertAv?.brukerIdent,
                reservertTom = reservasjon?.gyldigTil,
            )
        }
        val person = aktørIder.singleOrNull()?.let { aktørId ->
            pdlService.person(aktørId).person?.let { personPdl ->
                SøkeresultatPersonDto(
                    personPdl.navn(),
                    personPdl.fnr(),
                    personPdl.kjoenn(),
                    personPdl.doedsdato()
                )
            }
        }
        return if (person != null) {
            Søkeresultat.SøkeresultatMedPerson(person, oppgaverDto)
        } else {
            Søkeresultat.SøkeresultatUtenPerson(oppgaverDto)
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