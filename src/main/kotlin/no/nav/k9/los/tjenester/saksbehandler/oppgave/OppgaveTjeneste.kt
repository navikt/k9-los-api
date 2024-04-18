package no.nav.k9.los.tjenester.saksbehandler.oppgave

import kotlinx.coroutines.runBlocking
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.los.Configuration
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.aksjonspunktbehandling.AksjonspunktDefinisjonK9Tilbake
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.lager.oppgave.OppgaveMedId
import no.nav.k9.los.domene.lager.oppgave.Reservasjon
import no.nav.k9.los.domene.lager.oppgave.v2.OppgaveRepositoryV2
import no.nav.k9.los.domene.modell.*
import no.nav.k9.los.domene.repository.*
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.azuregraph.IAzureGraphService
import no.nav.k9.los.integrasjon.pdl.*
import no.nav.k9.los.integrasjon.rest.idToken
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.reservasjonkonvertering.ReservasjonOversetter
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.AlleOppgaverHistorikk
import no.nav.k9.los.tjenester.fagsak.PersonDto
import no.nav.k9.los.tjenester.mock.AksjonspunkterMock
import no.nav.k9.los.tjenester.saksbehandler.merknad.Merknad
import no.nav.k9.los.tjenester.saksbehandler.nokkeltall.NyeOgFerdigstilteOppgaverDto
import no.nav.k9.los.utils.Cache
import no.nav.k9.los.utils.CacheObject
import org.apache.commons.text.similarity.LevenshteinDistance
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
    private val oppgaveRepositoryV2: OppgaveRepositoryV2,
    private val oppgaveKøRepository: OppgaveKøRepository,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val pdlService: IPdlService,
    private val reservasjonRepository: ReservasjonRepository,
    private val configuration: Configuration,
    private val azureGraphService: IAzureGraphService,
    private val pepClient: IPepClient,
    private val statistikkRepository: StatistikkRepository,
    private val reservasjonOversetter: ReservasjonOversetter,
) {

    suspend fun hentOppgaver(oppgavekøId: UUID): List<Oppgave> {
        return try {
            log.info("Henter fra oppgavekø $oppgavekøId")
            val oppgaveKø = oppgaveKøRepository.hentOppgavekø(oppgavekøId)
            sorterOgHent(oppgaveKø)
        } catch (e: Exception) {
            log.error("Henting av oppgave feilet, returnerer en tom oppgaveliste", e)
            emptyList()
        }
    }

    private fun sorterOgHent(oppgaveKø: OppgaveKø): List<Oppgave> {
        if (oppgaveKø.beslutterKø()) {
            val oppgaver: List<Oppgave>
            val tid = measureTimeMillis {
                oppgaver = oppgaveRepository.hentOppgaver(oppgaveKø.oppgaverOgDatoer.map { it.id })
                    .sortedBy {
                        it.aksjonspunkter.beslutterAp()?.opprettetTidspunkt ?: it.behandlingOpprettet
                    }
            }

            ReservasjonRepository.RESERVASJON_YTELSE_LOG
                .info("sortering av beslutterkø med {} oppgaver tok {} ms", oppgaver.size, tid)
            return oppgaver.take(20)
        }

        if (oppgaveKø.sortering == KøSortering.FEILUTBETALT) {
            val oppgaver = oppgaveRepository.hentOppgaver(oppgaveKø.oppgaverOgDatoer.map { it.id })
            return oppgaver.sortedByDescending { it.feilutbetaltBeløp }
        }

        log.info("Køen ${oppgaveKø.id} har ${oppgaveKø.oppgaverOgDatoer.size} oppgaver")
        return oppgaveRepository.hentOppgaver(oppgaveKø.oppgaverOgDatoer.take(20).map { it.id })

    }

    suspend fun reserverOppgave(
        ident: String,
        overstyrIdent: String?,
        oppgaveUuid: UUID,
        overstyrSjekk: Boolean = false,
        overstyrBegrunnelse: String? = null
    ): OppgaveStatusDto {
        if (!pepClient.harTilgangTilReservingAvOppgaver()) {
            return OppgaveStatusDto(
                erReservert = false,
                reservertTilTidspunkt = null,
                erReservertAvInnloggetBruker = false,
                reservertAv = null,
                reservertAvNavn = null,
                flyttetReservasjon = null
            )
        }
        val oppgaveSomSkalBliReservert = oppgaveRepository.hent(oppgaveUuid)

        /*TODO midlertidig fjernet pga feil
        if (sjekkHvisSaksbehandlerPrøverOgReserverEnOppgaveDeSelvHarBesluttet(ident, oppgaveSomSkalBliReservert)) {
            return OppgaveStatusDto(
                erReservert = false,
                reservertTilTidspunkt = null,
                erReservertAvInnloggetBruker = false,
                reservertAv = null,
                reservertAvNavn = null,
                flyttetReservasjon = null,
                beskjed = Beskjed.BESLUTTET_AV_DEG
            )
        }
        */

        val oppgaverSomSkalBliReservert = mutableListOf<OppgaveMedId>()
        oppgaverSomSkalBliReservert.add(OppgaveMedId(oppgaveUuid, oppgaveSomSkalBliReservert))
        if (oppgaveSomSkalBliReservert.pleietrengendeAktørId != null) {
            val relaterteOppgaverSomSkalBliReservert = oppgaveRepository.hentOppgaverSomMatcher(
                oppgaveSomSkalBliReservert.pleietrengendeAktørId,
                oppgaveSomSkalBliReservert.fagsakYtelseType
            )

            oppgaverSomSkalBliReservert.addAll(
                filtrerOppgaveHvisBeslutter(
                    oppgaveSomSkalBliReservert,
                    relaterteOppgaverSomSkalBliReservert
                )
            )
        }

        var iderPåOppgaverSomSkalBliReservert = oppgaverSomSkalBliReservert.map { o -> o.id }.toSet()
        val gamleReservasjoner = reservasjonRepository.hent(iderPåOppgaverSomSkalBliReservert)
        val reserveresAvIdent = overstyrIdent ?: ident
        val aktiveReservasjoner =
            gamleReservasjoner.filter { rev -> rev.erAktiv() && rev.reservertAv != reserveresAvIdent }.toList()
        if (overstyrSjekk) {
            iderPåOppgaverSomSkalBliReservert = setOf(oppgaveUuid)
        } else {
            if (aktiveReservasjoner.isNotEmpty()) {
                val saksbehandler =
                    saksbehandlerRepository.finnSaksbehandlerMedIdent(aktiveReservasjoner[0].reservertAv)
                // todo endre til og kunen vise en liste her
                return OppgaveStatusDto(
                    erReservert = true,
                    reservertTilTidspunkt = aktiveReservasjoner[0].reservertTil,
                    erReservertAvInnloggetBruker = false,
                    reservertAv = aktiveReservasjoner[0].reservertAv,
                    reservertAvNavn = saksbehandler?.navn,
                    flyttetReservasjon = null,
                    kanOverstyres = true
                )
            }
        }
        val reservasjoner =
            lagReservasjoner(iderPåOppgaverSomSkalBliReservert, ident, overstyrIdent, overstyrBegrunnelse)

        reservasjonRepository.lagreFlereReservasjoner(reservasjoner)
        saksbehandlerRepository.leggTilFlereReservasjoner(reserveresAvIdent, reservasjoner.map { r -> r.oppgave })

        for (oppgavekø in oppgaveKøRepository.hentKøIdIkkeTaHensyn()) {
            oppgaveKøRepository.leggTilOppgaverTilKø(
                oppgavekø,
                oppgaverSomSkalBliReservert.map { o -> o.oppgave },
                reservasjonRepository
            )
        }

        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(reserveresAvIdent)
        return OppgaveStatusDto(
            erReservert = true,
            reservertTilTidspunkt = LocalDateTime.now().plusHours(48).forskyvReservasjonsDato(),
            erReservertAvInnloggetBruker = reservertAvMeg(reserveresAvIdent),
            reservertAv = reserveresAvIdent,
            reservertAvNavn = saksbehandler?.navn,
            flyttetReservasjon = null
        )
    }

    private fun filtrerOppgaveHvisBeslutter(
        oppgaveSomSkalBliReservert: Oppgave,
        relaterteOppgaverSomSkalBliReservert: List<OppgaveMedId>
    ): List<OppgaveMedId> {
        return if (erBeslutterOppgave(oppgaveSomSkalBliReservert)) {
            relaterteOppgaverSomSkalBliReservert.filter { o ->
                erBeslutterOppgave(o.oppgave)
            }
        } else {
            relaterteOppgaverSomSkalBliReservert.filter { o ->
                !erBeslutterOppgave(o.oppgave)
            }
        }
    }

    fun lagReservasjoner(
        iderPåOppgaverSomSkalBliReservert: Set<UUID>,
        ident: String,
        overstyrIdent: String?,
        begrunnelse: String? = null
    ): List<Reservasjon> {
        return iderPåOppgaverSomSkalBliReservert.map {
            val reservertTil = LocalDateTime.now().plusHours(48).forskyvReservasjonsDato()
            if (overstyrIdent != null) {
                Reservasjon(
                    reservertTil = reservertTil,
                    reservertAv = overstyrIdent,
                    flyttetAv = ident,
                    flyttetTidspunkt = LocalDateTime.now(),
                    begrunnelse = begrunnelse,
                    oppgave = it
                )
            } else {
                Reservasjon(
                    reservertTil = reservertTil,
                    reservertAv = ident,
                    flyttetAv = null,
                    flyttetTidspunkt = null,
                    begrunnelse = begrunnelse,
                    oppgave = it
                )
            }
        }.toList()
    }

    suspend fun søkFagsaker(query: String): SokeResultatDto {
        //TODO lage en bedre sjekk på om det er FNR
        //fnr
        if (query.length == 11) {
            return filtrerOppgaverForSaksnummerOgJournalpostIder(finnOppgaverBasertPåFnr(query))
        }

        //journalpost
        if (query.length == 9) {
            val oppgave = oppgaveRepository.hentOppgaveMedJournalpost(query)
            val oppgaverResultat = lagOppgaveDtoer(oppgave)
            if (oppgaverResultat.ikkeTilgang) {
                return SokeResultatDto(true, null, Collections.emptyList())
            }
            return SokeResultatDto(
                oppgaverResultat.ikkeTilgang,
                null,
                oppgaverResultat.oppgaver
            )
        }

        //TODO koble på omsorg når man kan søke på saksnummer
        val oppgaver = oppgaveRepository.hentOppgaverMedSaksnummer(query)
        val oppgaveResultat = lagOppgaveDtoer(oppgaver)

        if (oppgaveResultat.ikkeTilgang) {
            return SokeResultatDto(true, null, Collections.emptyList())
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
        val aktørIdFraFnr = pdlService.identifikator(query)

        if (aktørIdFraFnr.aktorId != null && aktørIdFraFnr.aktorId.data.hentIdenter != null && aktørIdFraFnr.aktorId.data.hentIdenter!!.identer.isNotEmpty()) {
            val aktorId = aktørIdFraFnr.aktorId.data.hentIdenter!!.identer[0].ident
            val person = pdlService.person(aktorId)
            if (person.person != null) {
                val personDto = mapTilPersonDto(person.person)
                val oppgaver = hentOppgaver(aktorId)

                return SokeResultatDto(
                    ikkeTilgang = person.ikkeTilgang,
                    person = personDto,
                    oppgaver = oppgaver
                )
            } else {
                return SokeResultatDto(
                    ikkeTilgang = person.ikkeTilgang,
                    person = null,
                    oppgaver = mutableListOf()
                )
            }
        }
        return SokeResultatDto(false, null, mutableListOf())
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
        val res = if (personInfo != null) {
            val personDto = mapTilPersonDto(personInfo)
            val oppgaver: MutableList<OppgaveDto> = hentOppgaver(aktørId)
            SokeResultatDto(
                ikkeTilgang = person.ikkeTilgang,
                person = personDto,
                oppgaver = oppgaver
            )
        } else {
            SokeResultatDto(
                ikkeTilgang = person.ikkeTilgang,
                person = null,
                oppgaver = mutableListOf()
            )
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

    private suspend fun hentOppgaver(aktorId: String): MutableList<OppgaveDto> {
        val oppgaver: List<Oppgave> = oppgaveRepository.hentOppgaverMedAktorId(aktorId)
        return lagOppgaveDtoer(oppgaver).oppgaver
    }

    private suspend fun lagOppgaveDtoer(oppgaver: List<Oppgave>): OppgaverResultat {
        var ikkeTilgang = false
        val oppgaver = oppgaver.filter { oppgave ->
            if (!pepClient.harTilgangTilOppgave(oppgave)) {
                settSkjermet(oppgave)
                ikkeTilgang = true
                false
            } else {
                true
            }
        }.map { oppgave ->
            tilOppgaveDto(
                oppgave = oppgave,
                reservasjon = reservasjonOversetter.hentAktivReservasjonFraGammelKontekst(oppgave),
                merknad = hentAktivMerknad(oppgave.eksternId.toString())
            )
        }.toMutableList()

        return OppgaverResultat(
            ikkeTilgang,
            oppgaver,
            oppgaver.any { it.merknad != null }
        )
    }

    fun hentAktivMerknad(eksternId: String): MerknadDto? {
        return oppgaveRepositoryV2.hentMerknader(eksternId, inkluderSlettet = false).firstOrNull().tilDto()
    }

    private fun Merknad?.tilDto(): MerknadDto? {
        return this?.let {
            MerknadDto(
                merknadKoder = it.merknadKoder,
                fritekst = it.fritekst ?: ""
            )
        }
    }

    private suspend fun reservertAvMeg(ident: String?): Boolean {
        return azureGraphService.hentIdentTilInnloggetBruker() == ident
    }

    private suspend fun tilOppgaveDto(oppgave: Oppgave, reservasjon: ReservasjonV3?, merknad: MerknadDto?): OppgaveDto {
        val oppgaveStatus =
            if (reservasjon == null) {
                OppgaveStatusDto(false, null, false, null, null, null)
            } else {
                val reservertAv = saksbehandlerRepository.finnSaksbehandlerMedId(reservasjon.reservertAv)
                val innloggetBruker  =
                    saksbehandlerRepository.finnSaksbehandlerMedIdent(azureGraphService.hentIdentTilInnloggetBruker())!!

                OppgaveStatusDto(
                    erReservert = true,
                    reservertTilTidspunkt = reservasjon.gyldigTil,
                    erReservertAvInnloggetBruker = reservertAv.id == innloggetBruker.id,
                    reservertAv = reservertAv.brukerIdent,
                    reservertAvNavn = reservertAv.navn,
                    flyttetReservasjon = null
                )
            }
        val person = pdlService.person(oppgave.aktorId)

        return if (oppgave.system == "PUNSJ") {
            val paaVent = oppgave.aksjonspunkter.hentAktive()["MER_INFORMASJON"]?.let { it == "OPPR" } == true
            oppgave.tilDto(oppgaveStatus, person, paaVent = paaVent, merknad = merknad)
        } else {
            oppgave.tilDto(oppgaveStatus, person, merknad = merknad)
        }
    }

    private fun Oppgave.tilDto(
        oppgaveStatus: OppgaveStatusDto,
        person: PersonPdlResponse,
        paaVent: Boolean? = null,
        merknad: MerknadDto?
    ): OppgaveDto {
        return OppgaveDto(
            status = oppgaveStatus,
            behandlingId = this.behandlingId,
            journalpostId = this.journalpostId,
            saksnummer = this.fagsakSaksnummer,
            navn = person.person?.navn() ?: "Ukjent navn",
            system = this.system,
            personnummer = if (person.person != null) {
                person.person.fnr()
            } else {
                "Ukjent fnummer"
            },
            behandlingstype = this.behandlingType,
            fagsakYtelseType = this.fagsakYtelseType,
            behandlingStatus = this.behandlingStatus,
            erTilSaksbehandling = this.aktiv || this.behandlingStatus.underBehandling(),
            opprettetTidspunkt = this.behandlingOpprettet,
            behandlingsfrist = this.behandlingsfrist,
            eksternId = this.eksternId,
            tilBeslutter = this.tilBeslutter,
            utbetalingTilBruker = this.utbetalingTilBruker,
            selvstendigFrilans = this.selvstendigFrilans,
            søktGradering = this.søktGradering,
            avklarArbeidsforhold = this.avklarArbeidsforhold,
            fagsakPeriode = this.fagsakPeriode,
            paaVent = paaVent,
            merknad = merknad
        )
    }

    suspend fun hentOppgaverFraListe(saksnummere: List<String>): List<OppgaveDto> {
        return saksnummere.flatMap { oppgaveRepository.hentOppgaverMedSaksnummer(it) }
            .map { oppgave ->
                tilOppgaveDto(
                    oppgave = oppgave,
                    reservasjon = reservasjonOversetter.hentAktivReservasjonFraGammelKontekst(oppgave),
                    merknad = hentAktivMerknad(oppgave.eksternId.toString())
                )
            }.toList()
    }

    suspend fun hentNyeOgFerdigstilteOppgaver(): List<NyeOgFerdigstilteOppgaverDto> {
        val hentIdentTilInnloggetBruker = azureGraphService.hentIdentTilInnloggetBruker()
        val alleTallene = statistikkRepository.hentFerdigstilteOgNyeHistorikkPerAntallDager(7)

        return alleTallene.map { it ->
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
        val ytelsetype = statistikkRepository.hentFerdigstilteOgNyeHistorikkMedYtelsetypeSiste8Uker()
        val ret = mutableListOf<AlleOppgaverHistorikk>()
        for (ytelseTypeEntry in ytelsetype.groupBy { it.fagsakYtelseType }) {
            val perBehandlingstype = ytelseTypeEntry.value.groupBy { it.behandlingType }
            for (behandlingTypeEntry in perBehandlingstype) {
                var aktive =
                    //TODO kjør i en transaksjon, helst som én spørring med group by
                    oppgaveRepository.hentAktiveOppgaverTotaltPerBehandlingstypeOgYtelseType(
                        fagsakYtelseType = ytelseTypeEntry.key,
                        behandlingType = behandlingTypeEntry.key
                    )
                behandlingTypeEntry.value.sortedByDescending { it.dato }.map {
                    aktive = if (aktive <= 0) {
                        0
                    } else {
                        val sum = aktive - it.nye.size + it.ferdigstilte.size
                        if (sum >= 0) sum else 0
                    }
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

    suspend fun endreReservasjonPåOppgave(resEndring: ReservasjonEndringDto): Reservasjon {
        val identTilInnloggetBruker = azureGraphService.hentIdentTilInnloggetBruker()
        val oppgavUUID = UUID.fromString(resEndring.oppgaveNøkkel.oppgaveEksternId)

        val oppdatertReservasjon = reservasjonRepository.lagre(oppgavUUID, true) {
            if (it == null) {
                throw IllegalArgumentException("Kan ikke oppdatere reservasjon som ikke finnes.")
            }
            if (resEndring.reserverTil != null) {
                it.reservertTil = LocalDateTime.of(
                    resEndring.reserverTil.year,
                    resEndring.reserverTil.month,
                    resEndring.reserverTil.dayOfMonth,
                    23,
                    59,
                    59
                ).forskyvReservasjonsDato()

            }
            if (resEndring.begrunnelse != null) {
                it.begrunnelse = resEndring.begrunnelse
            }
            if (resEndring.brukerIdent != null) {
                it.flyttetTidspunkt = LocalDateTime.now()
                it.reservertAv = resEndring.brukerIdent
                it.flyttetAv = identTilInnloggetBruker
            }
            it
        }
        if (resEndring.brukerIdent != null) {
            val reservasjon = reservasjonRepository.hent(oppgavUUID)
            saksbehandlerRepository.fjernReservasjon(reservasjon.reservertAv, reservasjon.oppgave)
            saksbehandlerRepository.leggTilReservasjon(resEndring.brukerIdent, reservasjon.oppgave)
        }
        return oppdatertReservasjon
    }

    suspend fun flyttReservasjon(uuid: UUID, ident: String, begrunnelse: String): Reservasjon {
        if (ident == "") {
            return reservasjonRepository.hent(uuid)
        }
        val hentIdentTilInnloggetBruker = azureGraphService.hentIdentTilInnloggetBruker()
        val oppdatertReservasjon = reservasjonRepository.lagre(uuid, true) {
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
        val reservasjon = reservasjonRepository.hent(uuid)
        saksbehandlerRepository.fjernReservasjon(reservasjon.reservertAv, reservasjon.oppgave)
        saksbehandlerRepository.leggTilReservasjon(ident, reservasjon.oppgave)

        return oppdatertReservasjon
    }

    suspend fun hentSisteBehandledeOppgaver(): List<BehandletOppgave> {
        return statistikkRepository.hentBehandlinger(coroutineContext.idToken().getUsername())
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

    private val hentAntallOppgaverCache = Cache<String, Int>()
    suspend fun hentAntallOppgaver(oppgavekøId: UUID, taMedReserverte: Boolean = false, refresh: Boolean = false): Int {
        val key = oppgavekøId.toString() + taMedReserverte
        if (!refresh) {
            val cacheObject = hentAntallOppgaverCache.get(key)
            if (cacheObject != null) {
                return cacheObject.value
            }
        }
        val oppgavekø = oppgaveKøRepository.hentOppgavekø(oppgavekøId, ignorerSkjerming = true)
        var reserverteOppgaverSomHørerTilKø = 0
        if (taMedReserverte) {
            val reservasjoner = reservasjonRepository.hentSelvOmDeIkkeErAktive(
                saksbehandlerRepository.hentAlleSaksbehandlereIkkeTaHensyn()
                    .flatMap { saksbehandler -> saksbehandler.reservasjoner }.toSet()
            )

            for (oppgave in oppgaveRepository.hentOppgaver(reservasjoner.map { it.oppgave })) {
                if (oppgavekø.tilhørerOppgaveTilKø(oppgave, reservasjonRepository, emptyList())) { //FIXME ?? tilhørerOppgaveTilKø sier alltid NEI når en oppgave er reservert, så hvordan virker dette?
                    reserverteOppgaverSomHørerTilKø++
                }
            }
            log.info("Antall reserverte oppgaver som ble lagt til var $reserverteOppgaverSomHørerTilKø")
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
                    if (!pepClient.harTilgangTilOppgave(oppgave)) {
                        settSkjermet(oppgave)
                        continue
                    }

                    val person = pdlService.person(oppgave.aktorId)

                    val navn = if (person.person != null) person.person.navn() else "Uten navn"

                    list.add(
                        lagOppgaveDto(oppgave, navn, person.person)
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

    private fun lagOppgaveDto(
        oppgave: Oppgave,
        navn: String,
        person: PersonPdl?,
    ) = OppgaveDto(
        status = OppgaveStatusDto(
            erReservert = false,
            reservertTilTidspunkt = null,
            erReservertAvInnloggetBruker = false,
            reservertAv = null,
            reservertAvNavn = null,
            flyttetReservasjon = null
        ),
        behandlingId = oppgave.behandlingId,
        saksnummer = oppgave.fagsakSaksnummer,
        journalpostId = oppgave.journalpostId,
        navn = navn,
        system = oppgave.system,
        personnummer = person?.fnr() ?: "Ukjent fnummer",
        behandlingstype = oppgave.behandlingType,
        fagsakYtelseType = oppgave.fagsakYtelseType,
        behandlingStatus = oppgave.behandlingStatus,
        erTilSaksbehandling = oppgave.aktiv || oppgave.behandlingStatus.underBehandling(),
        opprettetTidspunkt = oppgave.behandlingOpprettet,
        behandlingsfrist = oppgave.behandlingsfrist,
        eksternId = oppgave.eksternId,
        tilBeslutter = oppgave.tilBeslutter,
        utbetalingTilBruker = oppgave.utbetalingTilBruker,
        søktGradering = oppgave.søktGradering,
        selvstendigFrilans = oppgave.selvstendigFrilans,
        avklarArbeidsforhold = oppgave.avklarArbeidsforhold,
        merknad = hentAktivMerknad(oppgave.eksternId.toString())
    )

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

                val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(reservasjon.reservertAv)

                val status =
                    OppgaveStatusDto(
                        true,
                        reservasjon.reservertTil,
                        true,
                        reservasjon.reservertAv,
                        saksbehandler?.navn,
                        flyttetReservasjon = reservasjon.hentFlyttet()?.let { lagFlyttetReservasjonDto(it) }
                    )
                var personNavn: String
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
                val personFnummer: String = if (person.person == null) {
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
                        avklarArbeidsforhold = oppgave.avklarArbeidsforhold,
                        merknad = hentAktivMerknad(oppgave.eksternId.toString())
                    )
                )
            }
        }
        return list
    }

    private suspend fun lagFlyttetReservasjonDto(reservasjon: Reservasjon.Flyttet) =
        FlyttetReservasjonDto(
            reservasjon.flyttetTidspunkt,
            reservasjon.flyttetAv,
            saksbehandlerRepository.finnSaksbehandlerMedIdent(reservasjon.flyttetAv)?.navn ?: reservasjon.flyttetAv,
            reservasjon.begrunnelse
        )

    private suspend fun tilgangTilSak(oppgave: Oppgave): Boolean {
        if (!pepClient.harTilgangTilOppgave(oppgave)) {
            reservasjonRepository.lagre(oppgave.eksternId, true) {
                it!!.reservertTil = null
                runBlocking { saksbehandlerRepository.fjernReservasjon(it.reservertAv, it.oppgave) }
                it
            }
            settSkjermet(oppgave)
            oppgaveKøRepository.hent().forEach { oppgaveKø ->
                oppgaveKøRepository.lagre(oppgaveKø.id) {
                    it!!.leggOppgaveTilEllerFjernFraKø(
                        oppgave,
                        reservasjonRepository,
                        oppgaveRepositoryV2.hentMerknader(oppgave.eksternId.toString())
                    )
                    it
                }
            }
            return false
        }
        return true
    }

    suspend fun sokSaksbehandler(søkestreng: String): Saksbehandler {
        val alleSaksbehandlere = saksbehandlerRepository.hentAlleSaksbehandlere()

        fun levenshtein(lhs: CharSequence, rhs: CharSequence): Double {
            return LevenshteinDistance().apply(lhs, rhs).toDouble()
        }

        var d = Double.MAX_VALUE
        var i = -1
        for ((index, saksbehandler) in alleSaksbehandlere.withIndex()) {
            if (saksbehandler.brukerIdent == null) {
                continue
            }
            if (saksbehandler.navn != null && saksbehandler.navn!!.lowercase(Locale.getDefault())
                    .contains(søkestreng, true)
            ) {
                i = index
                break
            }

            var distance = levenshtein(
                søkestreng.lowercase(Locale.getDefault()),
                saksbehandler.brukerIdent!!.lowercase(Locale.getDefault())
            )
            if (distance < d) {
                d = distance
                i = index
            }
            distance = levenshtein(
                søkestreng.lowercase(Locale.getDefault()),
                saksbehandler.navn?.lowercase(Locale.getDefault()) ?: ""
            )
            if (distance < d) {
                d = distance
                i = index
            }
            distance = levenshtein(
                søkestreng.lowercase(Locale.getDefault()),
                saksbehandler.epost.lowercase(Locale.getDefault())
            )
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
        return statistikkRepository.lagreBehandling(ident, oppgave)
    }

    suspend fun settSkjermet(oppgave: Oppgave) {
        oppgaveRepository.lagre(oppgave.eksternId) {
            it!!
        }
        val oppaveSkjermet = oppgaveRepository.hent(oppgave.eksternId)
        for (oppgaveKø in oppgaveKøRepository.hent()) {
            val skalOppdareKø = oppgaveKø.leggOppgaveTilEllerFjernFraKø(
                oppaveSkjermet,
                reservasjonRepository,
                oppgaveRepositoryV2.hentMerknader(oppgave.eksternId.toString())
            )
            if (skalOppdareKø) {
                oppgaveKøRepository.lagre(oppgaveKø.id) {
                    it!!.leggOppgaveTilEllerFjernFraKø(
                        oppaveSkjermet,
                        reservasjonRepository,
                        oppgaveRepositoryV2.hentMerknader(oppgave.eksternId.toString())
                    )
                    it
                }
            }
        }
    }

    /** Henter første oppgave fra gitt kø med noen unntak
     *   - oppgave som har parsak som er reservert på annen saksbehandling blir hoppet over
     *   - oppgave som beslutter har besluttet blir hoppet over
     *   - oppgave som saksbehandler ikke har prioriet på blir hoppet over
     */
    suspend fun fåOppgaveFraKø(
        oppgaveKøId: String,
        brukerident: String,
        oppgaverSomErBlokert: MutableList<OppgaveDto> = emptyArray<OppgaveDto>().toMutableList(),
        prioriterOppgaverForSaksbehandler: List<Oppgave>? = null,
    ): OppgaveDto? {

        /* V3-versjon av denne logikken:
         få oppgave fra kø (V1 eller V3) -- versjonsagnostisk kandidat
         er nøkkelen reservert? -- neste kandidat  //evt filtrere vekk filtrerte først et annet sted?
         totrinnskontroll - utelukk beslutt egen saksbehandling og motsatt for alle oppgaver i reservasjon
         pepclent.harTilgangTilOppgave -- for alle oppgaver knyttet til nøkkel?
         */

        val nesteOppgaverIKø = hentNesteOppgaverIKø(UUID.fromString(oppgaveKøId))

        if (nesteOppgaverIKø.isEmpty()) {
            return null
        }

        val prioriterteOppgaver = prioriterOppgaverForSaksbehandler ?: finnPrioriterteOppgaver(
            brukerident,
            oppgaveKøId
        )

        val oppgavePar = finnOppgave(prioriterteOppgaver, nesteOppgaverIKø, oppgaverSomErBlokert) ?: return null

        val oppgaveDto: OppgaveDto

        if (oppgavePar.second != null) {
            val oppgave = oppgavePar.second!!
            val person = pdlService.person(oppgave.aktorId)

            oppgaveDto = lagOppgaveDto(oppgavePar.second!!, person.person?.navn() ?: "Ukjent", person.person)
            if (!pepClient.harTilgangTilOppgave(oppgave)) {
                log.info("OppgaveFraKø: Har ikke tilgang, setter skjermet på person")
                // skal ikke få oppgave saksbehandler ikke har lestilgang til
                settSkjermet(oppgavePar.second!!)
                oppgaverSomErBlokert.add(oppgaveDto)
                return fåOppgaveFraKø(
                    oppgaveKøId,
                    brukerident,
                    oppgaverSomErBlokert,
                    prioriterteOppgaver
                )
            }
        } else {
            oppgaveDto = oppgavePar.first!!
        }

        val oppgaveUuid = oppgaveDto.eksternId
        val oppgaveSomSkalBliReservert = oppgaveRepository.hent(oppgaveUuid)

        // beslutter skal ikke få opp oppgave med 5016 eller 5005 de selv har saksbehandlet
        if (innloggetSaksbehandlerHarSaksbehandletOppgaveSomSkalBliBesluttet(oppgaveSomSkalBliReservert, brukerident)) {
            log.info("OppgaveFraKø: Beslutter er saksbehandler på oppgaven")
            oppgaverSomErBlokert.add(oppgaveDto)
            return fåOppgaveFraKø(
                oppgaveKøId,
                brukerident,
                oppgaverSomErBlokert,
                prioriterteOppgaver
            )
        }

        // beslutter skal ikke få oppgaver de selv har besluttet
        if (innloggetSaksbehandlerHarBesluttetOppgaven(oppgaveSomSkalBliReservert, brukerident)) {
            log.info("OppgaveFraKø: Saksbehandler er beslutter på oppgaven")
            oppgaverSomErBlokert.add(oppgaveDto)
            return fåOppgaveFraKø(
                oppgaveKøId,
                brukerident,
                oppgaverSomErBlokert,
                prioriterteOppgaver
            )
        }

        val oppgaverSomSkalBliReservert = mutableListOf<OppgaveMedId>()
        oppgaverSomSkalBliReservert.add(OppgaveMedId(oppgaveUuid, oppgaveSomSkalBliReservert))
        if (oppgaveSomSkalBliReservert.pleietrengendeAktørId != null) {
            oppgaverSomSkalBliReservert.addAll(
                filtrerOppgaveHvisBeslutter(
                    oppgaveSomSkalBliReservert,
                    oppgaveRepository.hentOppgaverSomMatcher(
                        oppgaveSomSkalBliReservert.pleietrengendeAktørId,
                        oppgaveSomSkalBliReservert.fagsakYtelseType
                    )
                )
            )
        }

        val iderPåOppgaverSomSkalBliReservert = oppgaverSomSkalBliReservert.map { o -> o.id }.toSet()
        val gamleReservasjoner = reservasjonRepository.hent(iderPåOppgaverSomSkalBliReservert)
        val aktiveReservasjoner =
            gamleReservasjoner.filter { rev -> rev.erAktiv() && rev.reservertAv != brukerident }.toList()

        // skal ikke få oppgaver som tilhører en parsak der en av sakene er resvert på en annen saksbehandler
        if (aktiveReservasjoner.isNotEmpty()) {
            log.info("OppgaveFraKø: Prøver å reservere, men oppgaven er allerede reservert")
            oppgaverSomErBlokert.add(oppgaveDto)
            return fåOppgaveFraKø(
                oppgaveKøId,
                brukerident,
                oppgaverSomErBlokert,
                prioriterteOppgaver
            )
        }

        // sjekker også om parsakene har blitt besluttet av beslutter
        if (oppgaverSomSkalBliReservert.map { it.oppgave }
                .any { innloggetSaksbehandlerHarBesluttetOppgaven(it, brukerident) }) {
            log.info("OppgaveFraKø: Innlogget Saksbehandler har besluttet parsak")
            oppgaverSomErBlokert.add(oppgaveDto)
            return fåOppgaveFraKø(
                oppgaveKøId,
                brukerident,
                oppgaverSomErBlokert,
                prioriterteOppgaver
            )
        }

        // sjekker også om parsakene har blitt saksbehandlet av saksbehandler
        if (oppgaverSomSkalBliReservert.map { it.oppgave }
                .any { innloggetSaksbehandlerHarSaksbehandletOppgaveSomSkalBliBesluttet(it, brukerident) }) {
            log.info("OppgaveFraKø: Innlogget beslutter har saksbehandlet parsak")
            oppgaverSomErBlokert.add(oppgaveDto)
            return fåOppgaveFraKø(
                oppgaveKøId,
                brukerident,
                oppgaverSomErBlokert,
                prioriterteOppgaver
            )
        }

        val reservasjoner = lagReservasjoner(iderPåOppgaverSomSkalBliReservert, brukerident, null)

        //ReservasjonV3 TODO: sanity check - har noen andre reservert i ny modell? Hva skal skje da?
        val skalHaReservasjon = saksbehandlerRepository.finnSaksbehandlerMedIdent(brukerident)!!
        reservasjonOversetter.taNyReservasjonFraGammelKontekst(
            oppgaveV1 = oppgaveSomSkalBliReservert,
            reserverForSaksbehandlerId = skalHaReservasjon.id!!,
            reservertTil = LocalDateTime.now().plusHours(48).forskyvReservasjonsDato(),
            utførtAvSaksbehandlerId = skalHaReservasjon.id!!,
            kommentar = ""
        )

        reservasjonRepository.lagreFlereReservasjoner(reservasjoner)
        saksbehandlerRepository.leggTilFlereReservasjoner(brukerident, reservasjoner.map { r -> r.oppgave })

        for (oppgavekø in oppgaveKøRepository.hentKøIdIkkeTaHensyn()) {
            oppgaveKøRepository.leggTilOppgaverTilKø(
                oppgavekø,
                oppgaverSomSkalBliReservert.map { o -> o.oppgave },
                reservasjonRepository
            )
        }
        return oppgaveDto
    }

    private fun innloggetSaksbehandlerHarBesluttetOppgaven(
        oppgaveSomSkalBliReservert: Oppgave,
        ident: String
    ) = oppgaveSomSkalBliReservert.ansvarligBeslutterForTotrinn != null &&
            oppgaveSomSkalBliReservert.ansvarligBeslutterForTotrinn == ident &&
            !erBeslutterOppgave(oppgaveSomSkalBliReservert)

    private fun innloggetSaksbehandlerHarSaksbehandletOppgaveSomSkalBliBesluttet(
        oppgaveSomSkalBliReservert: Oppgave,
        ident: String
    ): Boolean {
        val besluttet = oppgaveSomSkalBliReservert.ansvarligSaksbehandlerForTotrinn != null &&
                oppgaveSomSkalBliReservert.ansvarligSaksbehandlerForTotrinn.equals(ident, true)

        //for gamle k9-tilbake behandlinger så er feltet ansvarligSaksbehandlerIdent brukt
        // istedenfor ansvarligSaksbehandlerForTotrinn, så sjekker begge.
        val besluttetK9Tilbake = oppgaveSomSkalBliReservert.ansvarligSaksbehandlerIdent != null &&
                oppgaveSomSkalBliReservert.ansvarligSaksbehandlerIdent.equals(ident, true)
                && oppgaveSomSkalBliReservert.system == Fagsystem.K9TILBAKE.kode

        return (besluttet || besluttetK9Tilbake) &&
                erBeslutterOppgave(oppgaveSomSkalBliReservert)
    }


    private fun erBeslutterOppgave(oppgaveSomSkalBliReservert: Oppgave) =
        oppgaveSomSkalBliReservert.aksjonspunkter.hentAktive().keys.any {
            it == AksjonspunktDefinisjon.FATTER_VEDTAK.kode
                    || it == AksjonspunktDefinisjonK9Tilbake.FATTE_VEDTAK.kode
        }

    private suspend fun finnPrioriterteOppgaver(
        ident: String,
        oppgaveKøId: String
    ): List<Oppgave> {
        val reservasjoneneTilSaksbehandler = reservasjonRepository.hent(ident).map { it.oppgave }
        if (reservasjoneneTilSaksbehandler.isEmpty()) {
            return emptyList()
        }

        val aktørIdFraReservasjonene =
            oppgaveRepository.hentOppgaver(reservasjoneneTilSaksbehandler).filter { it.pleietrengendeAktørId != null }
                .map { it.pleietrengendeAktørId!! }

        val køen = oppgaveKøRepository.hentOppgavekø(UUID.fromString(oppgaveKøId))
        val hentPleietrengendeAktør = oppgaveRepository.hentPleietrengendeAktør(køen.oppgaverOgDatoer.map { it.id })
        val oppgaverIder = aktørIdFraReservasjonene.mapNotNull { hentPleietrengendeAktør["\"" + it + "\""] }
            .map { UUID.fromString(it) }

        return oppgaveRepository.hentOppgaver(oppgaverIder)
    }

    private fun finnOppgave(
        prioriterOppgaver: List<Oppgave>,
        oppgaver: List<OppgaveDto>,
        oppgaverSomErBlokkert: MutableList<OppgaveDto>
    ): Pair<OppgaveDto?, Oppgave?>? {
        val prioriterteOppgaverSomIKkeErBlokkert =
            prioriterOppgaver.filter { !oppgaverSomErBlokkert.map { it2 -> it2.eksternId }.contains(it.eksternId) }

        val oppgaverSomIKkeErBlokkert =
            oppgaver.filter { !oppgaverSomErBlokkert.map { it2 -> it2.eksternId }.contains(it.eksternId) }

        if (prioriterteOppgaverSomIKkeErBlokkert.isNotEmpty()) {
            val oppgave = prioriterteOppgaverSomIKkeErBlokkert.first()
            return Pair(null, oppgave)
        }
        if (oppgaverSomIKkeErBlokkert.isEmpty()) {
            return null
        }
        return Pair(oppgaverSomIKkeErBlokkert.first(), null)
    }

}

private fun BehandlingStatus.underBehandling() = this != BehandlingStatus.AVSLUTTET && this != BehandlingStatus.LUKKET
