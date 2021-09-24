package no.nav.k9.tjenester.saksbehandler.oppgave

import info.debatty.java.stringsimilarity.Levenshtein
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import no.nav.k9.Configuration
import no.nav.k9.KoinProfile
import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.lager.oppgave.OppgaveMedId
import no.nav.k9.domene.lager.oppgave.Reservasjon
import no.nav.k9.domene.modell.*
import no.nav.k9.domene.repository.*
import no.nav.k9.integrasjon.abac.IPepClient
import no.nav.k9.integrasjon.azuregraph.IAzureGraphService
import no.nav.k9.integrasjon.kafka.dto.Fagsystem
import no.nav.k9.integrasjon.omsorgspenger.IOmsorgspengerService
import no.nav.k9.integrasjon.omsorgspenger.OmsorgspengerSakFnrDto
import no.nav.k9.integrasjon.pdl.*
import no.nav.k9.integrasjon.rest.idToken
import no.nav.k9.tjenester.avdelingsleder.nokkeltall.AlleOppgaverHistorikk
import no.nav.k9.tjenester.fagsak.PersonDto
import no.nav.k9.tjenester.mock.Aksjonspunkter
import no.nav.k9.tjenester.saksbehandler.nokkeltall.NyeOgFerdigstilteOppgaverDto
import no.nav.k9.utils.Cache
import no.nav.k9.utils.CacheObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*
import kotlin.coroutines.coroutineContext
import kotlin.system.measureTimeMillis

private val log: Logger =
    LoggerFactory.getLogger(OppgaveTjeneste::class.java)

class OppgaveTjeneste constructor(
    private val oppgaveRepository: OppgaveRepository,
    private val oppgaveKøRepository: OppgaveKøRepository,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val pdlService: IPdlService,
    private val reservasjonRepository: ReservasjonRepository,
    private val configuration: Configuration,
    private val azureGraphService: IAzureGraphService,
    private val pepClient: IPepClient,
    private val statistikkRepository: StatistikkRepository,
    private val omsorgspengerService: IOmsorgspengerService

) {

    fun hentOppgaver(oppgavekøId: UUID): List<Oppgave> {
        return try {
            val oppgaveKø = oppgaveKøRepository.hentOppgavekø(oppgavekøId)
            oppgaveRepository.hentOppgaver(oppgaveKø.oppgaverOgDatoer.take(20).map { it.id })
        } catch (e: Exception) {
            log.error("Henting av oppgave feilet, returnerer en tom oppgaveliste", e)
            emptyList()
        }
    }

    suspend fun reserverOppgave(ident: String, oppgaveUuid: UUID, overstyrSjekk: Boolean = false): OppgaveStatusDto {
        if (!pepClient.harTilgangTilReservingAvOppgaver()) {
            return OppgaveStatusDto(
                erReservert = false,
                reservertTilTidspunkt = null,
                erReservertAvInnloggetBruker = false,
                reservertAv = null,
                flyttetReservasjon = null
            )
        }
        val oppgaveSomSkalBliReservert = oppgaveRepository.hent(oppgaveUuid)
        val oppgaverSomSkalBliReservert = mutableListOf<OppgaveMedId>()
        oppgaverSomSkalBliReservert.add(OppgaveMedId(oppgaveUuid, oppgaveSomSkalBliReservert))
        if (oppgaveSomSkalBliReservert.pleietrengendeAktørId != null) {
            val relaterteOppgaverSomSkalBliReservert = oppgaveRepository.hentOppgaverSomMatcher(
                oppgaveSomSkalBliReservert.pleietrengendeAktørId,
                oppgaveSomSkalBliReservert.fagsakYtelseType
            )
            oppgaverSomSkalBliReservert.addAll(relaterteOppgaverSomSkalBliReservert)
        }

        var iderPåOppgaverSomSkalBliReservert = oppgaverSomSkalBliReservert.map { o -> o.id }.toSet()
        val gamleReservasjoner = reservasjonRepository.hent(iderPåOppgaverSomSkalBliReservert)
        val aktiveReservasjoner = gamleReservasjoner.filter { rev -> rev.erAktiv() && rev.reservertAv != ident }.toList()
        if (overstyrSjekk) {
            iderPåOppgaverSomSkalBliReservert = setOf(oppgaveUuid)
        } else {
            if (aktiveReservasjoner.isNotEmpty()) {
                // todo endre til og kunen vise en liste her
                return OppgaveStatusDto(
                    erReservert = true,
                    reservertTilTidspunkt = aktiveReservasjoner[0].reservertTil,
                    erReservertAvInnloggetBruker = false,
                    reservertAv = aktiveReservasjoner[0].reservertAv,
                    flyttetReservasjon = null
                )
            }
        }
        val reservasjoner = lagReservasjoner(iderPåOppgaverSomSkalBliReservert, ident)

        reservasjonRepository.lagreFlereReservasjoner(reservasjoner)
        saksbehandlerRepository.leggTilFlereReservasjoner(ident, reservasjoner.map { r -> r.oppgave })

        for (oppgavekø in oppgaveKøRepository.hentKøIdIkkeTaHensyn()) {
            oppgaveKøRepository.leggTilOppgaverTilKø(oppgavekø, oppgaverSomSkalBliReservert.map { o -> o.oppgave }, reservasjonRepository)
        }

        return OppgaveStatusDto(
            erReservert = true,
            reservertTilTidspunkt = LocalDateTime.now().plusHours(24).forskyvReservasjonsDato(),
            erReservertAvInnloggetBruker = reservertAvMeg(ident),
            reservertAv = ident,
            flyttetReservasjon = null
        )
    }

    private fun lagReservasjoner(
        iderPåOppgaverSomSkalBliReservert: Set<UUID>,
        ident: String
    ): List<Reservasjon> {
        return iderPåOppgaverSomSkalBliReservert.map {
            Reservasjon(
                reservertTil = LocalDateTime.now().plusHours(24).forskyvReservasjonsDato(),
                reservertAv = ident,
                flyttetAv = null,
                flyttetTidspunkt = null,
                begrunnelse = null,
                oppgave = it
            )
        }.toList()
    }

    suspend fun søkFagsaker(query: String): SokeResultatDto {
        //TODO lage en bedre sjekk på om det er FNR
        if (query.length == 11) {
            return filtrerOppgaverForSaksnummerOgJournalpostIder(finnOppgaverBasertPåFnr(query))
        }

        //TODO koble på omsorg når man kan søke på saksnummer
        val oppgaver = oppgaveRepository.hentOppgaverMedSaksnummer(query)
        val oppgaveResultat = lagOppgaveDtoer(oppgaver)

        if (oppgaveResultat.ikkeTilgang) {
            SokeResultatDto(true, null, Collections.emptyList())
        }
        return filtrerOppgaverForSaksnummerOgJournalpostIder(
            SokeResultatDto(
                oppgaveResultat.ikkeTilgang,
                null,
                oppgaveResultat.oppgaver
            )
        )
    }

    suspend fun aktiveOppgaverPåSammeBruker(oppgaveId: UUID): List<JournalpostId> {
        val oppgave = oppgaveRepository.hent(oppgaveId)
        val hentOppgaverMedAktorId = oppgaveRepository.hentOppgaverMedAktorId(oppgave.aktorId)
        return hentOppgaverMedAktorId.filter { o -> o.aktiv && o.system == Fagsystem.PUNSJ.kode && o.journalpostId != oppgave.journalpostId }
            .map { o -> JournalpostId(o.journalpostId!!) }
            .toList()
    }

    private fun filtrerOppgaverForSaksnummerOgJournalpostIder(dto: SokeResultatDto): SokeResultatDto {
        val oppgaver = dto.oppgaver

        val result = mutableListOf<OppgaveDto>()
        if (oppgaver.isNotEmpty()) {
            val bareJournalposter =
                oppgaver.filter { !it.journalpostId.isNullOrBlank() && it.saksnummer.isNullOrBlank() }

            result.addAll(bareJournalposter)
            oppgaver.removeAll(bareJournalposter)
            val oppgaverBySaksnummer = oppgaver.groupBy { it.saksnummer }
            for (entry in oppgaverBySaksnummer.entries) {
                val oppgaveDto = entry.value.firstOrNull { oppgaveDto -> oppgaveDto.erTilSaksbehandling }
                if (oppgaveDto != null) {
                    result.add(oppgaveDto)
                } else {
                    result.add(entry.value.first())
                }
            }
        }
        return SokeResultatDto(dto.ikkeTilgang, dto.person, result)
    }

    private suspend fun finnOppgaverBasertPåFnr(query: String): SokeResultatDto {
        var aktørIdFraFnr = pdlService.identifikator(query)

        val res = SokeResultatDto(false, null, mutableListOf())
        if (aktørIdFraFnr.aktorId != null && aktørIdFraFnr.aktorId!!.data.hentIdenter != null && aktørIdFraFnr.aktorId!!.data.hentIdenter!!.identer.isNotEmpty()) {
            var aktorId = aktørIdFraFnr.aktorId!!.data.hentIdenter!!.identer[0].ident
            val person = pdlService.person(aktorId)
            if (person.person != null) {
                val personDto = mapTilPersonDto(person.person)
                val oppgaver = hentOppgaver(aktorId)

                //sjekker om det finnes en visningsak i omsorgsdager
                val oppgaveDto = hentOmsorgsdagerForFnr(query, person.person.navn())
                if (oppgaveDto != null) {
                    oppgaver.add(oppgaveDto)
                }
                res.ikkeTilgang = person.ikkeTilgang
                res.person = personDto
                res.oppgaver.addAll(oppgaver)
            } else {
                res.ikkeTilgang = person.ikkeTilgang
                res.person = null
                res.oppgaver = mutableListOf()
            }
        }
        return res
    }

    suspend fun finnOppgaverBasertPåAktørId(aktørId: String): SokeResultatDto {
        var person = pdlService.person(aktørId)
        if (configuration.koinProfile() == KoinProfile.LOCAL) {
            person = PersonPdlResponse(
                false, PersonPdl(
                    data = PersonPdl.Data(
                        hentPerson = PersonPdl.Data.HentPerson(
                            folkeregisteridentifikator = listOf(
                                PersonPdl.Data.HentPerson.Folkeregisteridentifikator(
                                    "2392173967319"
                                )
                            ),
                            navn = listOf(
                                PersonPdl.Data.HentPerson.Navn(
                                    "Talentfull",
                                    null,
                                    "Dorull",
                                    null
                                )
                            ),
                            kjoenn = listOf(
                                PersonPdl.Data.HentPerson.Kjoenn(
                                    "MANN"
                                )
                            ),
                            doedsfall = listOf()
                        )
                    )
                )
            )
        }
        val personInfo = person.person
        val res = SokeResultatDto(false, null, mutableListOf())
        if (personInfo != null) {
            val personDto = mapTilPersonDto(personInfo)
            val oppgaver: MutableList<OppgaveDto> = hentOppgaver(aktørId)
            //sjekker om det finnes en visningsak i omsorgsdager
            val oppgaveDto = hentOmsorgsdagerForFnr(personInfo.fnr(), personInfo.navn())
            if (oppgaveDto != null) {
                oppgaver.add(oppgaveDto)
            }
            res.ikkeTilgang = person.ikkeTilgang
            res.person = personDto
            res.oppgaver.addAll(oppgaver)
        } else {
            res.ikkeTilgang = person.ikkeTilgang
            res.person = null
            res.oppgaver = mutableListOf()
        }
        return filtrerOppgaverForSaksnummerOgJournalpostIder(res)
    }

    private fun mapTilPersonDto(person: PersonPdl): PersonDto {
        return PersonDto(
            person.navn(),
            person.fnr(),
            person.kjoenn(),
            null
            //   person.data.hentPerson.doedsfall[0].doedsdato
        )
    }

    private suspend fun hentOmsorgsdagerForFnr(
        fnr: String,
        navn: String
    ): OppgaveDto? {
        val omsorgspengerSakDto = omsorgspengerService.hentOmsorgspengerSakDto(OmsorgspengerSakFnrDto(fnr))

        if (omsorgspengerSakDto != null) {
            val statusDto = OppgaveStatusDto(
                erReservert = false,
                reservertTilTidspunkt = null,
                erReservertAvInnloggetBruker = false,
                reservertAv = null,
                flyttetReservasjon = null
            )
            //TODO fyll ut denne bedre?
            return OppgaveDto(
                statusDto,
                null,
                null,
                omsorgspengerSakDto.saksnummer,
                navn,
                Fagsystem.OMSORGSPENGER.kode,
                fnr,
                BehandlingType.FORSTEGANGSSOKNAD,
                FagsakYtelseType.OMSORGSDAGER,
                BehandlingStatus.OPPRETTET,
                true,
                LocalDateTime.now(),
                LocalDateTime.now(),
                UUID.randomUUID(),
                tilBeslutter = false,
                utbetalingTilBruker = false,
                avklarArbeidsforhold = false,
                selvstendigFrilans = false,
                søktGradering = false,
            )
        }
        return null
    }

    private suspend fun hentOppgaver(aktorId: String): MutableList<OppgaveDto> {
        val oppgaver: List<Oppgave> = oppgaveRepository.hentOppgaverMedAktorId(aktorId)
        return lagOppgaveDtoer(oppgaver).oppgaver
    }

    private suspend fun lagOppgaveDtoer(oppgaver: List<Oppgave>): OppgaverResultat {
        var ikkeTilgang = false
        val res = oppgaver.filter { oppgave ->
            if (!pepClient.harTilgangTilLesSak(
                    fagsakNummer = oppgave.fagsakSaksnummer,
                    aktørid = oppgave.aktorId
                )
            ) {
                settSkjermet(oppgave)
                ikkeTilgang = true
                false
            } else {
                true
            }
        }.map {
            tilOppgaveDto(
                oppgave = it, reservasjon = if (reservasjonRepository.finnes(it.eksternId)
                    && reservasjonRepository.hent(it.eksternId).erAktiv()
                ) {
                    reservasjonRepository.hent(it.eksternId)
                } else {
                    null
                }
            )
        }.toMutableList()

        return OppgaverResultat(ikkeTilgang, res)
    }

    suspend fun reservertAvMeg(ident: String?): Boolean {
        return azureGraphService.hentIdentTilInnloggetBruker() == ident
    }

    suspend fun tilOppgaveDto(oppgave: Oppgave, reservasjon: Reservasjon?): OppgaveDto {
        val oppgaveStatus =
            if (reservasjon != null && (!reservasjon.erAktiv()) || reservasjon == null) {
                OppgaveStatusDto(false, null, false, null, null)
            } else {
                OppgaveStatusDto(
                    erReservert = true,
                    reservertTilTidspunkt = reservasjon.reservertTil,
                    erReservertAvInnloggetBruker = reservertAvMeg(reservasjon.reservertAv),
                    reservertAv = reservasjon.reservertAv,
                    flyttetReservasjon = null
                )
            }
        val person = pdlService.person(oppgave.aktorId)

        if (oppgave.system == "PUNSJ") {
            return OppgaveDto(
                status = oppgaveStatus,
                behandlingId = oppgave.behandlingId,
                journalpostId = oppgave.journalpostId,
                saksnummer = oppgave.fagsakSaksnummer,
                navn = person.person?.navn() ?: "Ukjent navn",
                system = oppgave.system,
                personnummer = if (person.person != null) {
                    person.person.fnr()
                } else {
                    "Ukjent fnummer"
                },
                behandlingstype = oppgave.behandlingType,
                fagsakYtelseType = oppgave.fagsakYtelseType,
                behandlingStatus = oppgave.behandlingStatus,
                erTilSaksbehandling = oppgave.aktiv,
                opprettetTidspunkt = oppgave.behandlingOpprettet,
                behandlingsfrist = oppgave.behandlingsfrist,
                eksternId = oppgave.eksternId,
                tilBeslutter = oppgave.tilBeslutter,
                utbetalingTilBruker = oppgave.utbetalingTilBruker,
                selvstendigFrilans = oppgave.selvstendigFrilans,
                søktGradering = oppgave.søktGradering,
                avklarArbeidsforhold = oppgave.avklarArbeidsforhold,
                fagsakPeriode = oppgave.fagsakPeriode,
                paaVent = if (oppgave.aksjonspunkter.liste["MER_INFORMASJON"] != null) oppgave.aksjonspunkter.liste["MER_INFORMASJON"] == "OPPR" else false
            )
        } else {
            return OppgaveDto(
                status = oppgaveStatus,
                behandlingId = oppgave.behandlingId,
                journalpostId = oppgave.journalpostId,
                saksnummer = oppgave.fagsakSaksnummer,
                navn = person.person?.navn() ?: "Ukjent navn",
                system = oppgave.system,
                personnummer = if (person.person != null) {
                    person.person.fnr()
                } else {
                    "Ukjent fnummer"
                },
                behandlingstype = oppgave.behandlingType,
                fagsakYtelseType = oppgave.fagsakYtelseType,
                behandlingStatus = oppgave.behandlingStatus,
                erTilSaksbehandling = oppgave.aktiv,
                opprettetTidspunkt = oppgave.behandlingOpprettet,
                behandlingsfrist = oppgave.behandlingsfrist,
                eksternId = oppgave.eksternId,
                tilBeslutter = oppgave.tilBeslutter,
                utbetalingTilBruker = oppgave.utbetalingTilBruker,
                selvstendigFrilans = oppgave.selvstendigFrilans,
                søktGradering = oppgave.søktGradering,
                avklarArbeidsforhold = oppgave.avklarArbeidsforhold,
                fagsakPeriode = oppgave.fagsakPeriode
            )
        }
    }

    suspend fun hentOppgaverFraListe(saksnummere: List<String>): List<OppgaveDto> {
        return saksnummere.flatMap { oppgaveRepository.hentOppgaverMedSaksnummer(it) }
            .map { oppgave ->
                tilOppgaveDto(
                    oppgave = oppgave, reservasjon = if (reservasjonRepository.finnes(oppgave.eksternId)
                        && reservasjonRepository.hent(oppgave.eksternId).erAktiv()
                    ) {
                        reservasjonRepository.hent(oppgave.eksternId)
                    } else {
                        null
                    }
                )
            }.toList()
    }

    suspend fun hentNyeOgFerdigstilteOppgaver(): List<NyeOgFerdigstilteOppgaverDto> {
        val omsorgspengerYtelser = listOf(
            FagsakYtelseType.OMSORGSPENGER,
            FagsakYtelseType.OMSORGSDAGER,
            FagsakYtelseType.OMSORGSPENGER_KS,
            FagsakYtelseType.OMSORGSPENGER_MA,
            FagsakYtelseType.OMSORGSPENGER_AO)
        val alleTallene = statistikkRepository.hentFerdigstilteOgNyeHistorikkPerAntallDager(7)
        alleTallene.forEach {
            if (omsorgspengerYtelser.contains(it.fagsakYtelseType)) it.fagsakYtelseType = FagsakYtelseType.OMSORGSPENGER
        }

        return alleTallene.map {
            val hentIdentTilInnloggetBruker = azureGraphService.hentIdentTilInnloggetBruker()
            val antallFerdistilteMine =
                reservasjonRepository.hentSelvOmDeIkkeErAktive(it.ferdigstilte.map { UUID.fromString(it)!! }
                    .toSet())
                    .filter { it.reservertAv == hentIdentTilInnloggetBruker }.size
            NyeOgFerdigstilteOppgaverDto(
                behandlingType = it.behandlingType,
                fagsakYtelseType = it.fagsakYtelseType,
                dato = it.dato,
                antallNye = it.nye.size,
                antallFerdigstilte = it.ferdigstilteSaksbehandler.size,
                antallFerdigstilteMine = antallFerdistilteMine
            )
        }
    }

    fun hentBeholdningAvOppgaverPerAntallDager(): List<AlleOppgaverHistorikk> {
        val ytelsetype =
            statistikkRepository.hentFerdigstilteOgNyeHistorikkMedYtelsetypeSiste8Uker()
        val ret = mutableListOf<AlleOppgaverHistorikk>()
        for (ytelseTypeEntry in ytelsetype.groupBy { it.fagsakYtelseType }) {
            val perBehandlingstype = ytelseTypeEntry.value.groupBy { it.behandlingType }
            for (behandlingTypeEntry in perBehandlingstype) {
                var aktive =
                    oppgaveRepository.hentAktiveOppgaverTotaltPerBehandlingstypeOgYtelseType(
                        fagsakYtelseType = ytelseTypeEntry.key,
                        behandlingType = behandlingTypeEntry.key
                    )
                behandlingTypeEntry.value.sortedByDescending { it.dato }.map {
                    aktive = aktive - it.nye.size + it.ferdigstilte.size
                    ret.add(
                        AlleOppgaverHistorikk(
                            it.fagsakYtelseType,
                            it.behandlingType,
                            it.dato,
                            aktive
                        )
                    )
                }
            }
        }
        return ret
    }

    suspend fun frigiReservasjon(uuid: UUID, begrunnelse: String): Reservasjon {
        val reservasjon = reservasjonRepository.lagre(uuid, true) {
            it!!.begrunnelse = begrunnelse
            it.reservertTil = null
            it
        }
        saksbehandlerRepository.fjernReservasjon(reservasjon.reservertAv, reservasjon.oppgave)
        val oppgave = oppgaveRepository.hent(uuid)
        for (oppgavekø in oppgaveKøRepository.hent()) {
            oppgaveKøRepository.leggTilOppgaverTilKø(oppgavekø.id, listOf(oppgave), reservasjonRepository)
        }
        return reservasjon
    }

    fun forlengReservasjonPåOppgave(uuid: UUID): Reservasjon {
        return reservasjonRepository.lagre(uuid, true) {
            it!!.reservertTil = it.reservertTil?.plusHours(24)!!.forskyvReservasjonsDato()
            it
        }
    }

    fun endreReservasjonPåOppgave(resEndring: ReservasjonEndringDto): Reservasjon {
        return reservasjonRepository.lagre(UUID.fromString(resEndring.oppgaveId), true) {
            it!!.reservertTil = LocalDateTime.of(
                resEndring.reserverTil.year,
                resEndring.reserverTil.month,
                resEndring.reserverTil.dayOfMonth,
                23,
                59,
                59
            ).forskyvReservasjonsDato()
            it
        }
    }

    suspend fun flyttReservasjon(uuid: UUID, ident: String, begrunnelse: String): Reservasjon {
        if (ident == "") {
            return reservasjonRepository.hent(uuid)
        }
        val hentIdentTilInnloggetBruker = azureGraphService.hentIdentTilInnloggetBruker()
        val reservasjon = reservasjonRepository.hent(uuid)
        saksbehandlerRepository.fjernReservasjon(reservasjon.reservertAv, reservasjon.oppgave)
        saksbehandlerRepository.leggTilReservasjon(ident, reservasjon.oppgave)
        return reservasjonRepository.lagre(uuid, true) {
            if (it!!.reservertTil == null) {
                it.reservertTil = LocalDateTime.now().plusHours(24).forskyvReservasjonsDato()
            } else {
                it.reservertTil = it.reservertTil?.plusHours(24)!!.forskyvReservasjonsDato()
            }
            it.flyttetTidspunkt = LocalDateTime.now()
            it.reservertAv = ident
            it.flyttetAv = hentIdentTilInnloggetBruker
            it.begrunnelse = begrunnelse
            it
        }
    }

    suspend fun hentSisteBehandledeOppgaver(): List<BehandletOppgave> {
        return statistikkRepository.hentBehandlinger(coroutineContext.idToken().getUsername())
    }

    suspend fun flyttReservasjonTilForrigeSakbehandler(uuid: UUID) {
        val reservasjoner = reservasjonRepository.hentMedHistorikk(uuid).reversed()
        for (reservasjon in reservasjoner) {
            if (reservasjoner[0].reservertAv != reservasjon.reservertAv) {
                reservasjonRepository.lagre(uuid, true) {

                    it!!.reservertAv = reservasjon.reservertAv
                    it.reservertTil = LocalDateTime.now().plusDays(3).forskyvReservasjonsDato()
                    it
                }

                saksbehandlerRepository.fjernReservasjon(reservasjon.reservertAv, reservasjon.oppgave)
                saksbehandlerRepository.leggTilReservasjon(
                    reservasjon.reservertAv,
                    reservasjon.oppgave
                )
                return
            }
        }
    }

    fun hentReservasjonsHistorikk(uuid: UUID): ReservasjonHistorikkDto {
        val reservasjoner = reservasjonRepository.hentMedHistorikk(uuid).reversed()
        return ReservasjonHistorikkDto(
            reservasjoner = reservasjoner.map {
                ReservasjonDto(
                    reservertTil = it.reservertTil,
                    reservertAv = it.reservertAv,
                    flyttetAv = it.flyttetAv,
                    flyttetTidspunkt = it.flyttetTidspunkt,
                    begrunnelse = it.begrunnelse
                )
            }.toList(),
            oppgaveId = uuid.toString()
        )
    }

    private val hentAntallOppgaverCache = Cache<Int>()
    fun hentAntallOppgaver(oppgavekøId: UUID, taMedReserverte: Boolean = false, refresh: Boolean = false): Int {
        val key = oppgavekøId.toString() + taMedReserverte
        if (!refresh) {
            val cacheObject = hentAntallOppgaverCache.get(key)
            if (cacheObject != null) {
                return cacheObject.value
            }
        }
        val oppgavekø = oppgaveKøRepository.hentOppgavekø(oppgavekøId)
        var reserverteOppgaverSomHørerTilKø = 0
        if (taMedReserverte) {
            val reservasjoner = reservasjonRepository.hentSelvOmDeIkkeErAktive(
                saksbehandlerRepository.hentAlleSaksbehandlereIkkeTaHensyn()
                    .flatMap { saksbehandler -> saksbehandler.reservasjoner }.toSet()
            )

            for (oppgave in oppgaveRepository.hentOppgaver(reservasjoner.map { it.oppgave })) {
                if (oppgavekø.tilhørerOppgaveTilKø(oppgave, reservasjonRepository)) {
                    reserverteOppgaverSomHørerTilKø++
                }
            }
        }
        val antall = oppgavekø.oppgaverOgDatoer.size + reserverteOppgaverSomHørerTilKø
        hentAntallOppgaverCache.set(key, CacheObject(antall, LocalDateTime.now().plusMinutes(30)))
        return antall
    }

    suspend fun hentAntallOppgaverTotalt(): Int {
        return oppgaveRepository.hentAktiveOppgaverTotalt()
    }

    suspend fun hentNesteOppgaverIKø(kø: UUID): List<OppgaveDto> {
        if (pepClient.harBasisTilgang()) {
            val list = mutableListOf<OppgaveDto>()
            val ms = measureTimeMillis {
                for (oppgave in hentOppgaver(kø)) {
                    if (list.size == 10) {
                        break
                    }
                    if (!pepClient.harTilgangTilLesSak(
                            fagsakNummer = oppgave.fagsakSaksnummer,
                            aktørid = oppgave.aktorId
                        )
                    ) {
                        settSkjermet(oppgave)
                        continue
                    }

                    val person = pdlService.person(oppgave.aktorId)

                    val navn = if (KoinProfile.PREPROD == configuration.koinProfile()) {
                        preprodNavn(oppgave)
                    } else {
                        if (person.person != null) person.person.navn() else "Uten navn"
                    }
                    list.add(
                        OppgaveDto(
                            status = OppgaveStatusDto(
                                erReservert = false,
                                reservertTilTidspunkt = null,
                                erReservertAvInnloggetBruker = false,
                                reservertAv = null,
                                flyttetReservasjon = null
                            ),
                            behandlingId = oppgave.behandlingId,
                            saksnummer = oppgave.fagsakSaksnummer,
                            journalpostId = oppgave.journalpostId,
                            navn = navn,
                            system = oppgave.system,
                            personnummer = if (person.person == null) {
                                "Ukjent fnummer"
                            } else {
                                person.person?.fnr()
                            },
                            behandlingstype = oppgave.behandlingType,
                            fagsakYtelseType = oppgave.fagsakYtelseType,
                            behandlingStatus = oppgave.behandlingStatus,
                            erTilSaksbehandling = oppgave.aktiv,
                            opprettetTidspunkt = oppgave.behandlingOpprettet,
                            behandlingsfrist = oppgave.behandlingsfrist,
                            eksternId = oppgave.eksternId,
                            tilBeslutter = oppgave.tilBeslutter,
                            utbetalingTilBruker = oppgave.utbetalingTilBruker,
                            søktGradering = oppgave.søktGradering,
                            selvstendigFrilans = oppgave.selvstendigFrilans,
                            avklarArbeidsforhold = oppgave.avklarArbeidsforhold
                        )
                    )
                }
            }
            log.info("Hentet ${list.size} oppgaver for oppgaveliste tok $ms")
            return list
        } else {
            log.warn("har ikke basistilgang")
        }
        return emptyList()
    }

    private fun preprodNavn(oppgave: Oppgave): String {
        return "${oppgave.fagsakSaksnummer} " +
                oppgave.aksjonspunkter.liste.entries.map { t ->
                    val a = Aksjonspunkter().aksjonspunkter()
                        .find { aksjonspunkt -> aksjonspunkt.kode == t.key }
                    "${t.key} ${a?.navn ?: "Ukjent aksjonspunkt"}"
                }.toList().joinToString(", ")
    }

    suspend fun hentSisteReserverteOppgaver(): List<OppgaveDto> {
        val list = mutableListOf<OppgaveDto>()
        //Hent reservasjoner for en gitt bruker skriv om til å hente med ident direkte i tabellen
        val saksbehandlerMedEpost =
            saksbehandlerRepository.finnSaksbehandlerMedEpost(coroutineContext.idToken().getUsername())
        val brukerIdent = saksbehandlerMedEpost?.brukerIdent ?: return emptyList()
        val reservasjoner = reservasjonRepository.hent(brukerIdent)
        for (reservasjon in reservasjoner
            .sortedBy { reservasjon -> reservasjon.reservertTil }) {
            val oppgave = oppgaveRepository.hentHvis(reservasjon.oppgave)
            if (oppgave == null) {
                saksbehandlerRepository.fjernReservasjon(brukerIdent, reservasjon.oppgave)
            } else {
                if (!tilgangTilSak(oppgave)) continue

                val person = pdlService.person(oppgave.aktorId)

                val status =
                    OppgaveStatusDto(
                        true,
                        reservasjon.reservertTil,
                        true,
                        reservasjon.reservertAv,
                        flyttetReservasjon = if (reservasjon.flyttetAv.isNullOrEmpty()) {
                            null
                        } else {
                            FlyttetReservasjonDto(
                                reservasjon.flyttetTidspunkt!!,
                                reservasjon.flyttetAv!!,
                                saksbehandlerRepository.finnSaksbehandlerMedIdent(reservasjon.flyttetAv!!)?.navn!!,
                                reservasjon.begrunnelse!!
                            )
                        }
                    )
                var personNavn: String
                var personFnummer: String
                val navn = if (KoinProfile.PREPROD == configuration.koinProfile()) {
                    "${oppgave.fagsakSaksnummer} "
                  //          oppgave.aksjonspunkter.liste.entries.stream().map { t ->
                  //              val a = Aksjonspunkter().aksjonspunkter()
                 //                   .find { aksjonspunkt -> aksjonspunkt.kode == t.key }
                 //               "${t.key} ${a?.navn ?: "Ukjent aksjonspunkt"}"
                 //           }.toList().joinToString(", ")
                } else {
                    person.person?.navn() ?: "Uten navn"
                }
                personNavn = navn
                personFnummer = if (person.person == null) {
                    "Ukjent fnummer"
                } else {
                    person.person.fnr()
                }
                list.add(
                    OppgaveDto(
                        status = status,
                        behandlingId = oppgave.behandlingId,
                        saksnummer = oppgave.fagsakSaksnummer,
                        journalpostId = oppgave.journalpostId,
                        navn = personNavn,
                        system = oppgave.system,
                        personnummer = personFnummer,
                        behandlingstype = oppgave.behandlingType,
                        fagsakYtelseType = oppgave.fagsakYtelseType,
                        behandlingStatus = oppgave.behandlingStatus,
                        erTilSaksbehandling = true,
                        opprettetTidspunkt = oppgave.behandlingOpprettet,
                        behandlingsfrist = oppgave.behandlingsfrist,
                        eksternId = oppgave.eksternId,
                        tilBeslutter = oppgave.tilBeslutter,
                        utbetalingTilBruker = oppgave.utbetalingTilBruker,
                        selvstendigFrilans = oppgave.selvstendigFrilans,
                        søktGradering = oppgave.søktGradering,
                        avklarArbeidsforhold = oppgave.avklarArbeidsforhold
                    )
                )
            }
        }
        return list
    }

    suspend fun tilgangTilSak(oppgave: Oppgave): Boolean {
        if (!pepClient.harTilgangTilLesSak(
                fagsakNummer = oppgave.fagsakSaksnummer,
                aktørid = oppgave.aktorId
            )
        ) {
            reservasjonRepository.lagre(oppgave.eksternId, true) {
                it!!.reservertTil = null
                runBlocking { saksbehandlerRepository.fjernReservasjon(it.reservertAv, it.oppgave) }
                it
            }
            settSkjermet(oppgave)
            oppgaveKøRepository.hent().forEach { oppgaveKø ->
                oppgaveKøRepository.lagre(oppgaveKø.id) {
                    it!!.leggOppgaveTilEllerFjernFraKø(oppgave, reservasjonRepository)
                    it
                }
            }
            return false
        }
        return true
    }

    suspend fun sokSaksbehandlerMedIdent(ident: BrukerIdentDto): Saksbehandler? {
        return saksbehandlerRepository.finnSaksbehandlerMedIdent(ident.brukerIdent)
    }

    suspend fun sokSaksbehandler(søkestreng: String): Saksbehandler? {
        val alleSaksbehandlere = saksbehandlerRepository.hentAlleSaksbehandlere()
        val levenshtein = Levenshtein()

        var d = Double.MAX_VALUE
        var i = -1
        for ((index, saksbehandler) in alleSaksbehandlere.withIndex()) {
            if (saksbehandler.brukerIdent == null) {
                continue
            }
            if (saksbehandler.navn != null && saksbehandler.navn!!.toLowerCase().contains(søkestreng, true)) {
                i = index
                break
            }

            var distance = levenshtein.distance(søkestreng.toLowerCase(), saksbehandler.brukerIdent!!.toLowerCase())
            if (distance < d) {
                d = distance
                i = index
            }
            distance = levenshtein.distance(søkestreng.toLowerCase(), saksbehandler.navn!!.toLowerCase())
            if (distance < d) {
                d = distance
                i = index
            }
            distance = levenshtein.distance(søkestreng.toLowerCase(), saksbehandler.epost.toLowerCase())
            if (distance < d) {
                d = distance
                i = index
            }
        }
        return alleSaksbehandlere[i]
    }

    suspend fun hentOppgaveKøer(): List<OppgaveKø> {
        return oppgaveKøRepository.hent()
    }

    fun leggTilBehandletOppgave(ident: String, oppgave: BehandletOppgave) {
        return statistikkRepository.lagreBehandling(ident) {
            oppgave
        }
    }

    suspend fun settSkjermet(oppgave: Oppgave) {
        oppgaveRepository.lagre(oppgave.eksternId) {
            it!!
        }
        val oppaveSkjermet = oppgaveRepository.hent(oppgave.eksternId)
        for (oppgaveKø in oppgaveKøRepository.hent()) {
            val skalOppdareKø = oppgaveKø.leggOppgaveTilEllerFjernFraKø(oppaveSkjermet, reservasjonRepository)
            if (skalOppdareKø) {
                oppgaveKøRepository.lagre(oppgaveKø.id) {
                    it!!.leggOppgaveTilEllerFjernFraKø(oppaveSkjermet, reservasjonRepository)
                    it
                }
            }
        }
    }
}
