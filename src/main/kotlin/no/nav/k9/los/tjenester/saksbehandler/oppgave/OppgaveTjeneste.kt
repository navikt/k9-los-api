package no.nav.k9.los.tjenester.saksbehandler.oppgave

import kotlinx.coroutines.channels.Channel
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.los.Configuration
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.aksjonspunktbehandling.AksjonspunktDefinisjonK9Tilbake
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.lager.oppgave.OppgaveMedId
import no.nav.k9.los.domene.lager.oppgave.Reservasjon
import no.nav.k9.los.domene.modell.*
import no.nav.k9.los.domene.repository.*
import no.nav.k9.los.eventhandler.DetaljerMetrikker
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.azuregraph.IAzureGraphService
import no.nav.k9.los.integrasjon.pdl.*
import no.nav.k9.los.integrasjon.rest.idToken
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.reservasjonkonvertering.ReservasjonOversetter
import no.nav.k9.los.nyoppgavestyring.reservasjon.BeslutterErSaksbehandlerException
import no.nav.k9.los.nyoppgavestyring.reservasjon.ManglerTilgangException
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.OppgaverGruppertRepository
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.AlleOppgaverHistorikk
import no.nav.k9.los.tjenester.fagsak.PersonDto
import no.nav.k9.los.tjenester.saksbehandler.nokkeltall.NyeOgFerdigstilteOppgaverDto
import no.nav.k9.los.utils.Cache
import no.nav.k9.los.utils.CacheObject
import no.nav.k9.los.utils.leggTilDagerHoppOverHelg
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
    private val koinProfile: KoinProfile
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

    suspend fun hentSisteBehandledeOppgaver(): List<BehandletOppgave> {
        return statistikkRepository.hentBehandlinger(coroutineContext.idToken().getUsername())
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
        if (koinProfile == KoinProfile.PROD) {
            return oppgaveRepository.hentAktiveOppgaverTotalt()
        } else {
            val harTilgangTilKode6 = pepClient.harTilgangTilKode6()
            return oppgaverGruppertRepository.hentTotaltAntallÅpneOppgaver(harTilgangTilKode6)
        }
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
        if (!pepClient.harBasisTilgang()) {
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

        val oppgaverPlukketFraKø = DetaljerMetrikker.timeSuspended("faaOppgaveFraKo", "hentOppgaver") {
            hentOppgaver(oppgaveKø)
        }

        var antallOppgaverSjekket = 0
        for (oppgave in prioriterteOppgaver + oppgaverPlukketFraKø) {
            //prøv å reservere i V3
            //sjekk om returnert reservasjon er holdt av meg
            //hvis holdt av meg -- ferdig
            //hvis ikke holdt av meg -- allerede reservert -> continue
            //catch ManglerTilgangException -> continue
            antallOppgaverSjekket++

            //ReservasjonV3 TODO: sanity check - har noen andre reservert i ny modell? Hva skal skje da?
            val saksbehandlerSomReserverer = DetaljerMetrikker.timeSuspended(
                "faaOppgaveFraKo",
                "finnSaksbehandlerMedIdent"
            ) { saksbehandlerRepository.finnSaksbehandlerMedIdent(brukerident)!! }

            try {
                val reservasjon =
                    DetaljerMetrikker.timeSuspended("faaOppgaveFraKo", "taNyReservasjonFraGammelKontekst") {
                        reservasjonOversetter.taNyReservasjonFraGammelKontekst(
                            oppgaveV1 = oppgave,
                            reserverForSaksbehandlerId = saksbehandlerSomReserverer.id!!,
                            reservertTil = LocalDateTime.now().leggTilDagerHoppOverHelg(2),
                            utførtAvSaksbehandlerId = saksbehandlerSomReserverer.id!!,
                            kommentar = ""
                        )
                    }
                if (reservasjon.reservertAv != saksbehandlerSomReserverer.id) { // noen andre har allerede reservert
                    continue
                }
            } catch (e: ManglerTilgangException) {
                settSkjermet(oppgave)
                log.info("OppgaveFraKø: Har ikke tilgang, setter skjermet på oppgaven")
                continue
            } catch (e: BeslutterErSaksbehandlerException) {
                log.info("Saksbehandler kan ikke være beslutter på egen oppgave, eller motsatt: ${oppgave.eksternId}")
                continue
            }

            val oppgaveDto =
                lagOppgaveDto(
                    oppgave,
                    null
                )

            statistikkChannel.send(true)

            DetaljerMetrikker.observe(starttid, "faaOppgaveFraKo", "iterasjoner", "${antallOppgaverSjekket}")
            return oppgaveDto
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
