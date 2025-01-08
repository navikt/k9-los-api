package no.nav.k9.los.tjenester.saksbehandler.oppgave

import kotlinx.coroutines.channels.Channel
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
import no.nav.k9.los.eventhandler.DetaljerMetrikker
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.azuregraph.IAzureGraphService
import no.nav.k9.los.integrasjon.pdl.*
import no.nav.k9.los.integrasjon.rest.idToken
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.reservasjonkonvertering.ReservasjonOversetter
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.OppgaverGruppertRepository
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.AlleOppgaverHistorikk
import no.nav.k9.los.tjenester.fagsak.PersonDto
import no.nav.k9.los.tjenester.saksbehandler.nokkeltall.NyeOgFerdigstilteOppgaverDto
import no.nav.k9.los.utils.Cache
import no.nav.k9.los.utils.CacheObject
import no.nav.k9.los.utils.forskyvReservasjonsDato
import no.nav.k9.los.utils.leggTilDagerHoppOverHelg
import org.apache.commons.text.similarity.LevenshteinDistance
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.coroutines.coroutineContext
import kotlin.system.measureTimeMillis

private val log: Logger =
    LoggerFactory.getLogger(OppgaveTjeneste::class.java)

class OppgaveTjeneste constructor(
    private val oppgaveRepository: OppgaveRepository,
    private val oppgaverGruppertRepository: OppgaverGruppertRepository,
    private val oppgaveKøRepository: OppgaveKøRepository,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val pdlService: IPdlService,
    private val reservasjonRepository: ReservasjonRepository,
    private val configuration: Configuration,
    private val azureGraphService: IAzureGraphService,
    private val pepClient: IPepClient,
    private val statistikkRepository: StatistikkRepository,
    private val reservasjonOversetter: ReservasjonOversetter,
    private val statistikkChannel: Channel<Boolean>,
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

    fun hentOppgaver(oppgaveKø: OppgaveKø): List<Oppgave> {
        return try {
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
            ReservasjonRepository.RESERVASJON_YTELSE_LOG.info(
                "sortering av beslutterkø med {} oppgaver tok {} ms",
                oppgaver.size,
                tid
            )
            return oppgaver.take(20)
        }

        if (oppgaveKø.sortering == KøSortering.FEILUTBETALT) {
            val oppgaver: List<Oppgave>
            val tid = measureTimeMillis {
                oppgaver = oppgaveRepository.hentOppgaver(oppgaveKø.oppgaverOgDatoer.map { it.id })
                    .sortedByDescending { it.feilutbetaltBeløp }
            }
            ReservasjonRepository.RESERVASJON_YTELSE_LOG.info(
                "sortering av tilbakekrevingkø med {} oppgaver tok {} ms",
                oppgaver.size,
                tid
            )
            return oppgaver
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
        if (!pepClient.harTilgangTilReserveringAvOppgaver()) {
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
        val gamleReservasjoner =
            reservasjonRepository.hentOgFjernInaktiveReservasjoner(iderPåOppgaverSomSkalBliReservert)
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

        for (oppgavekø in oppgaveKøRepository.hentKøIdInkluderKode6()) {
            oppgaveKøRepository.leggTilOppgaverTilKø(
                oppgavekø,
                oppgaverSomSkalBliReservert.map { o -> o.oppgave },
                erOppgavenReservertSjekk = { true } //reserveres rett over
            )
        }
        statistikkChannel.send(true)

        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(reserveresAvIdent)
        return OppgaveStatusDto(
            erReservert = true,
            reservertTilTidspunkt = LocalDateTime.now().leggTilDagerHoppOverHelg(2),
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
            val reservertTil = LocalDateTime.now().leggTilDagerHoppOverHelg(2)
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
            )
        }.toMutableList()

        return OppgaverResultat(
            ikkeTilgang,
            oppgaver
        )
    }


    private suspend fun reservertAvMeg(ident: String?): Boolean {
        return azureGraphService.hentIdentTilInnloggetBruker() == ident
    }

    private suspend fun tilOppgaveDto(oppgave: Oppgave, reservasjon: ReservasjonV3?): OppgaveDto {
        val oppgaveStatus =
            if (reservasjon == null) {
                OppgaveStatusDto(false, null, false, null, null, null)
            } else {
                val reservertAv = saksbehandlerRepository.finnSaksbehandlerMedId(reservasjon.reservertAv)!!
                val innloggetBruker =
                    saksbehandlerRepository.finnSaksbehandlerMedIdent(azureGraphService.hentIdentTilInnloggetBruker())

                OppgaveStatusDto(
                    erReservert = true,
                    reservertTilTidspunkt = reservasjon.gyldigTil,
                    erReservertAvInnloggetBruker = innloggetBruker?.let { reservertAv.id == it.id } ?: false,
                    reservertAv = reservertAv?.brukerIdent,
                    reservertAvNavn = reservertAv?.navn,
                    flyttetReservasjon = null
                )
            }
        val person = pdlService.person(oppgave.aktorId)

        return oppgave.tilDto(
            oppgaveStatus,
            person,
            paaVent = oppgave.aksjonspunkter.påVent(Fagsystem.fraKode(oppgave.system))
        )
    }

    private fun Oppgave.tilDto(
        oppgaveStatus: OppgaveStatusDto,
        person: PersonPdlResponse,
        paaVent: Boolean? = null,
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
            paaVent = paaVent
        )
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
            log.info("Frigir reservasjonen $uuid som var holdt av ${it.reservertAv}")
            it
        }
        saksbehandlerRepository.fjernReservasjon(reservasjon.reservertAv, reservasjon.oppgave)
        val oppgave = oppgaveRepository.hent(uuid)
        for (oppgavekø in oppgaveKøRepository.hentAlle()) {
            oppgaveKøRepository.leggTilOppgaverTilKø(
                oppgavekø.id,
                listOf(oppgave),
                erOppgavenReservertSjekk = { false }) //reservasjon ble nettopp fjernet
        }
        return reservasjon
    }

    fun forlengReservasjonPåOppgave(uuid: UUID): Reservasjon {
        return reservasjonRepository.lagre(uuid, true) {
            it!!.reservertTil = it.reservertTil?.leggTilDagerHoppOverHelg(1)
            log.info("Forlenger reservasjonen $uuid til ${it.reservertTil}, som var holdt av ${it.reservertAv}")
            it
        }
    }

    suspend fun endreReservasjonPåOppgave(
        oppgaveNøkkel: OppgaveNøkkelDto,
        tilBrukerIdent: String? = null,
        reserverTil: LocalDate? = null,
        begrunnelse: String? = null
    ): Reservasjon {
        val identTilInnloggetBruker = azureGraphService.hentIdentTilInnloggetBruker()
        val oppgavUUID = UUID.fromString(oppgaveNøkkel.oppgaveEksternId)

        val oppdatertReservasjon = reservasjonRepository.lagre(oppgavUUID, true) {
            if (it == null) {
                throw IllegalArgumentException("Kan ikke oppdatere reservasjon som ikke finnes.")
            }
            if (reserverTil != null) {
                it.reservertTil = LocalDateTime.of(
                    reserverTil.year,
                    reserverTil.month,
                    reserverTil.dayOfMonth,
                    23,
                    59,
                    59
                ).forskyvReservasjonsDato()

            }
            if (begrunnelse != null) {
                it.begrunnelse = begrunnelse
            }
            if (tilBrukerIdent != null) {
                it.flyttetTidspunkt = LocalDateTime.now()
                it.reservertAv = tilBrukerIdent
                it.flyttetAv = identTilInnloggetBruker
            }
            it
        }
        if (tilBrukerIdent != null) {
            val reservasjon = reservasjonRepository.hent(oppgavUUID)
            saksbehandlerRepository.fjernReservasjon(reservasjon.reservertAv, reservasjon.oppgave)
            saksbehandlerRepository.leggTilReservasjon(tilBrukerIdent, reservasjon.oppgave)
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
                it.reservertTil = LocalDateTime.now().leggTilDagerHoppOverHelg(1)
            } else {
                it.reservertTil = it.reservertTil?.leggTilDagerHoppOverHelg(1)
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

    private data class CacheKey(val kø: UUID, val medReserverte: Boolean)

    private val hentAntallOppgaverCache = Cache<CacheKey, Int>(cacheSizeLimit = 1000)

    suspend fun refreshAntallForAlleKøer() {
        val køene = DetaljerMetrikker.timeSuspended("refreshAntallForAlleKøer", "hent")
        { oppgaveKøRepository.hentAlleInkluderKode6() }
        val reservasjonIder = DetaljerMetrikker.timeSuspended("refreshAntallForAlleKøer", "hentReservasjonIder")
        {
            saksbehandlerRepository.hentAlleSaksbehandlereInkluderKode6()
                .flatMap { saksbehandler -> saksbehandler.reservasjoner }.toSet()
        }
        val reserverteOppgaveIderDirekte =
            DetaljerMetrikker.timeSuspended("refreshAntallForAlleKøer", "hentUUIDForOppgaverMedAktivReservasjon")
            { reservasjonRepository.hentOppgaveUuidMedAktivReservasjon(reservasjonIder) }
        val reserverteOppgaver = DetaljerMetrikker.timeSuspended("refreshAntallForAlleKøer", "hentReserverteOppgaver")
        { oppgaveRepository.hentOppgaver(reserverteOppgaveIderDirekte) }
        køene.forEach {
            DetaljerMetrikker.timeSuspended(
                "refreshAntallForAlleKøer",
                "refreshHentAntallOppgaverForKo"
            ) { refreshHentAntallOppgaverForKø(it, reserverteOppgaver) }
        }
    }

    fun refreshAntallOppgaverForKø(oppgavekø: OppgaveKø) {
        val reservasjonIder = saksbehandlerRepository.hentAlleSaksbehandlereInkluderKode6()
            .flatMap { saksbehandler -> saksbehandler.reservasjoner }.toSet()
        val reserverteOppgaveIder = reservasjonRepository.hentOppgaveUuidMedAktivReservasjon(reservasjonIder)
        val reserverteOppgaver = oppgaveRepository.hentOppgaver(reserverteOppgaveIder)
        refreshHentAntallOppgaverForKø(oppgavekø, reserverteOppgaver)
    }

    private fun refreshHentAntallOppgaverForKø(
        oppgavekø: OppgaveKø,
        reserverteOppgaver: List<Oppgave>
    ) {
        val antallReserverteOppgaverSomTilhørerKø =
            reserverteOppgaver.count {
                oppgavekø.tilhørerOppgaveTilKø(
                    it,
                    erOppgavenReservertSjekk = { false },
                )
            } //må spesifikt si at oppgaven ikke er reservert for å telle den reserverte oppgaven
        val antallUtenReserverte = oppgavekø.oppgaverOgDatoer.size
        val antallMedReserverte = oppgavekø.oppgaverOgDatoer.size + antallReserverteOppgaverSomTilhørerKø
        hentAntallOppgaverCache.set(
            CacheKey(oppgavekø.id, false),
            CacheObject(antallUtenReserverte, LocalDateTime.now().plusMinutes(30))
        )
        hentAntallOppgaverCache.set(
            CacheKey(oppgavekø.id, true),
            CacheObject(antallMedReserverte, LocalDateTime.now().plusMinutes(30))
        )

        log.info("Refreshet antall for kø ${oppgavekø.id}. Antall i kø er ${antallUtenReserverte} og i tillegg kommer ${antallReserverteOppgaverSomTilhørerKø} reserverte oppgaver som tilhører køen")
    }

    suspend fun hentAntallOppgaver(oppgavekøId: UUID, taMedReserverte: Boolean = false, refresh: Boolean = false): Int {
        val key = CacheKey(oppgavekøId, taMedReserverte)
        if (!refresh) {
            val cacheObject = hentAntallOppgaverCache.get(key)
            if (cacheObject != null) {
                return cacheObject.value
            }
        }
        val oppgavekø = oppgaveKøRepository.hentOppgavekø(oppgavekøId, ignorerSkjerming = true)
        var antallReserverteOppgaverSomTilhørerKø = 0
        if (taMedReserverte) {
            val reservasjonIder = saksbehandlerRepository.hentAlleSaksbehandlereInkluderKode6()
                .flatMap { saksbehandler -> saksbehandler.reservasjoner }.toSet()
            val reserverteOppgaveIder = reservasjonRepository.hentOppgaveUuidMedAktivReservasjon(reservasjonIder)
            val reserverteOppgaver = oppgaveRepository.hentOppgaver(reserverteOppgaveIder)

            antallReserverteOppgaverSomTilhørerKø =
                reserverteOppgaver.count {
                    oppgavekø.tilhørerOppgaveTilKø(
                        it,
                        erOppgavenReservertSjekk = { false },
                    )
                } //må spesifikt si at oppgaven ikke er reservert for å telle den reserverte oppgaven
            log.info("Antall reserverte oppgaver som ble lagt til var $antallReserverteOppgaverSomTilhørerKø for køen ${oppgavekø.navn}")
        }
        val antall = oppgavekø.oppgaverOgDatoer.size + antallReserverteOppgaverSomTilhørerKø
        hentAntallOppgaverCache.set(key, CacheObject(antall, LocalDateTime.now().plusMinutes(30)))
        return antall
    }

    suspend fun hentAntallOppgaverTotalt(): Int {
        val harTilgangTilKode6 = pepClient.harTilgangTilKode6()
        return oppgaverGruppertRepository.hentTotaltAntallÅpneOppgaver(harTilgangTilKode6)
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

                    // Sjekker om det finnes en v3-reservasjon. F.eks ved retur fra beslutter der v1-reservasjonen er annullert mens v3-reservasjonen reaktiveres
                    if (reservasjonOversetter.hentAktivReservasjonFraGammelKontekst(oppgave)?.erAktiv() == true) {
                        log.info("OppgaveFraKø: Reservasjon v1 er ute av synk med v3. Fjerner oppgave med eksisterende v3-reservasjon fra kandidater ${oppgave.eksternId}")
                        continue
                    }

                    val person = pdlService.person(oppgave.aktorId)
                    list.add(
                        lagOppgaveDto(oppgave, person.person)
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
        navn = person?.navn() ?: "Uten navn",
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
        avklarArbeidsforhold = oppgave.avklarArbeidsforhold
    )

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
        return oppgaveKøRepository.hentAlle()
    }

    fun leggTilBehandletOppgave(ident: String, oppgave: BehandletOppgave) {
        return statistikkRepository.lagreBehandling(ident, oppgave)
    }

    suspend fun settSkjermet(oppgave: Oppgave) {
        oppgaveRepository.lagre(oppgave.eksternId) {
            it!!
        }
        val oppaveSkjermet = oppgaveRepository.hent(oppgave.eksternId)
        for (oppgaveKø in oppgaveKøRepository.hentAlle()) {
            val skalOppdareKø = oppgaveKø.leggOppgaveTilEllerFjernFraKø(
                oppaveSkjermet,
                reservasjonRepository,
            )
            if (skalOppdareKø) {
                oppgaveKøRepository.lagre(oppgaveKø.id) {
                    it!!.leggOppgaveTilEllerFjernFraKø(
                        oppaveSkjermet,
                        reservasjonRepository,
                    )
                    it
                }
            }
        }
    }

    suspend fun fåOppgaveFraKø(
        oppgaveKøId: String,
        brukerident: String,
    ): OppgaveDto? {
        val starttid = System.nanoTime()
        if (DetaljerMetrikker.timeSuspended("faaOppgaveFraKo", "basistilgang") { !pepClient.harBasisTilgang() }) {
            log.warn("har ikke basistilgang")
            return null
        }
        val oppgaveKø = DetaljerMetrikker.timeSuspended(
            "faaOppgaveFraKo",
            "hentOppgaveKø"
        ) { oppgaveKøRepository.hentOppgavekø(UUID.fromString(oppgaveKøId)) }
        val prioriterteOppgaver = DetaljerMetrikker.timeSuspended("faaOppgaveFraKo", "finnPrioriterteOppgaver") {
            finnPrioriterteOppgaver(
                brukerident,
                oppgaveKø
            )
        }
        val oppgaverPlukketFraKø =
            DetaljerMetrikker.timeSuspended("faaOppgaveFraKo", "hentOppgaver") { hentOppgaver(oppgaveKø) }

        var antallOppgaverSjekket = 0
        for (oppgave in prioriterteOppgaver + oppgaverPlukketFraKø) {
            antallOppgaverSjekket++
            val oppgaveUuid = oppgave.eksternId
            val fraVanligKø = prioriterteOppgaver.none() { it.eksternId == oppgaveUuid }
            if (DetaljerMetrikker.timeSuspended(
                    "faaOppgaveFraKo",
                    "tilgangTilOppgave"
                ) { !pepClient.harTilgangTilOppgave(oppgave) }
            ) {
                settSkjermet(oppgave)
                log.info("OppgaveFraKø: Har ikke tilgang, setter skjermet på oppgaven")
            } else if (fraVanligKø && DetaljerMetrikker.timeSuspended(
                    "faaOppgaveFraKo",
                    "hentAktivReservasjonFraGammelKontekst"
                ) { reservasjonOversetter.hentAktivReservasjonFraGammelKontekst(oppgave)?.erAktiv() == true }
            ) {
                log.info("OppgaveFraKø: Reservasjon v1 er ute av synk med v3. Hopper over oppgave med eksisterende v3-reservasjon $oppgaveUuid")
            } else if (innloggetSaksbehandlerHarSaksbehandletOppgaveSomSkalBliBesluttet(oppgave, brukerident)) {
                log.info("OppgaveFraKø: Beslutter er saksbehandler på oppgaven")
            } else if (innloggetSaksbehandlerHarBesluttetOppgaven(oppgave, brukerident)) {
                log.info("OppgaveFraKø: Saksbehandler er beslutter på oppgaven")
            } else {
                val oppgaverSomSkalBliReservert = mutableListOf<OppgaveMedId>()
                oppgaverSomSkalBliReservert.add(OppgaveMedId(oppgaveUuid, oppgave))
                if (oppgave.pleietrengendeAktørId != null) {
                    oppgaverSomSkalBliReservert.addAll(
                        filtrerOppgaveHvisBeslutter(
                            oppgave,
                            oppgaveRepository.hentOppgaverSomMatcher(
                                oppgave.pleietrengendeAktørId,
                                oppgave.fagsakYtelseType
                            )
                        )
                    )
                }
                val iderPåOppgaverSomSkalBliReservert = oppgaverSomSkalBliReservert.map { o -> o.id }.toSet()
                log.info("OppgaveFraKø: Prøver å reservere ${iderPåOppgaverSomSkalBliReservert.joinToString(", ")} for $brukerident")

                val gamleReservasjoner = DetaljerMetrikker.timeSuspended(
                    "faaOppgaveFraKo",
                    "gamleReservasjoner"
                ) { reservasjonRepository.hentOgFjernInaktiveReservasjoner(iderPåOppgaverSomSkalBliReservert) }
                val aktiveReservasjoner =
                    gamleReservasjoner.filter { rev -> rev.erAktiv() && rev.reservertAv != brukerident }.toList()


                if (aktiveReservasjoner.isNotEmpty()) {
                    // skal ikke få oppgaver som tilhører en parsak der en av sakene er resvert på en annen saksbehandler
                    log.info("OppgaveFraKø: Prøver å reservere for $brukerident, men oppgaven er allerede reservert av aktiv reservasjon: ${aktiveReservasjoner.joinToString { it.oppgave.toString() + it.reservertTil }}")
                } else if (oppgaverSomSkalBliReservert.any {
                        innloggetSaksbehandlerHarBesluttetOppgaven(
                            it.oppgave,
                            brukerident
                        )
                    }) {
                    // sjekker også om parsakene har blitt besluttet av beslutter
                    log.info("OppgaveFraKø: Innlogget Saksbehandler har besluttet parsak")
                } else if (oppgaverSomSkalBliReservert.any {
                        innloggetSaksbehandlerHarSaksbehandletOppgaveSomSkalBliBesluttet(
                            it.oppgave,
                            brukerident
                        )
                    }) {
                    // sjekker også om parsakene har blitt saksbehandlet av saksbehandler
                    log.info("OppgaveFraKø: Innlogget beslutter har saksbehandlet parsak")
                } else {
                    val reservasjoner = DetaljerMetrikker.timeSuspended("faaOppgaveFraKo", "lagReservasjoner") {
                        lagReservasjoner(
                            iderPåOppgaverSomSkalBliReservert,
                            brukerident,
                            null
                        )
                    }

                    //ReservasjonV3 TODO: sanity check - har noen andre reservert i ny modell? Hva skal skje da?
                    val skalHaReservasjon = DetaljerMetrikker.timeSuspended(
                        "faaOppgaveFraKo",
                        "finnSaksbehandlerMedIdent"
                    ) { saksbehandlerRepository.finnSaksbehandlerMedIdent(brukerident)!! }
                    DetaljerMetrikker.timeSuspended("faaOppgaveFraKo", "taNyReservasjonFraGammelKontekst") {
                        reservasjonOversetter.taNyReservasjonFraGammelKontekst(
                            oppgaveV1 = oppgave,
                            reserverForSaksbehandlerId = skalHaReservasjon.id!!,
                            reservertTil = LocalDateTime.now().leggTilDagerHoppOverHelg(2),
                            utførtAvSaksbehandlerId = skalHaReservasjon.id!!,
                            kommentar = ""
                        )
                    }

                    DetaljerMetrikker.timeSuspended("faaOppgaveFraKo", "lagreFlereReservasjoner") {
                        reservasjonRepository.lagreFlereReservasjoner(reservasjoner)
                    }
                    DetaljerMetrikker.timeSuspended("faaOppgaveFraKo", "leggTilFlereReservasjoner") {
                        saksbehandlerRepository.leggTilFlereReservasjoner(
                            brukerident,
                            reservasjoner.map { r -> r.oppgave })
                    }
                    DetaljerMetrikker.timeSuspended("faaOppgaveFraKo", "leggTilOppgaverTilKø") {
                        for (oppgavekø in oppgaveKøRepository.hentKøIdInkluderKode6()) {
                            oppgaveKøRepository.leggTilOppgaverTilKø(
                                oppgavekø,
                                oppgaverSomSkalBliReservert.map { o -> o.oppgave },
                                erOppgavenReservertSjekk = { true } //vi har reservert oppgavene rett over, så kan hardkode her at oppgaven er reservert
                            )
                        }
                    }

                    val person = null //evt. personinfo returnert her vises ikke i frontend
                    val oppgaveDto = DetaljerMetrikker.timeSuspended("faaOppgaveFraKo", "lagOppgaveDto") {
                        lagOppgaveDto(
                            oppgave,
                            person
                        )
                    }

                    statistikkChannel.send(true)

                    DetaljerMetrikker.observe(starttid, "faaOppgaveFraKo", "iterasjoner", "${antallOppgaverSjekket}")
                    return oppgaveDto
                }

            }
        }
        log.info("Fant ingen oppgaver til saksbehandler i køen ${oppgaveKøId}")
        return null
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
        oppgaveKø: OppgaveKø
    ): List<Oppgave> {
        val reservasjoneneTilSaksbehandler =
            reservasjonRepository.hentOgFjernInaktiveReservasjoner(ident).map { it.oppgave }
        if (reservasjoneneTilSaksbehandler.isEmpty()) {
            return emptyList()
        }

        val aktørIdFraReservasjonene = DetaljerMetrikker.timeSuspended("faaOppgaveFraKo", "aktørIdFraReservasjonene") {
            oppgaveRepository.hentOppgaver(reservasjoneneTilSaksbehandler).filter { it.pleietrengendeAktørId != null }
                .map { it.pleietrengendeAktørId!! }
        }

        val oppgaverIder = DetaljerMetrikker.timeSuspended(
            "faaOppgaveFraKo",
            "oppgaverForPleietrengendeAktør"
        ) {
            oppgaveRepository.hentOppgaverForPleietrengendeAktør(
                oppgaveKø.oppgaverOgDatoer.map { it.id },
                aktørIdFraReservasjonene
            )
        }
        return oppgaveRepository.hentOppgaver(oppgaverIder)
    }

}

private fun BehandlingStatus.underBehandling() = this != BehandlingStatus.AVSLUTTET && this != BehandlingStatus.LUKKET
