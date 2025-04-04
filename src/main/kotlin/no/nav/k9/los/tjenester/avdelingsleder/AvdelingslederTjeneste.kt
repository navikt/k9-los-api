package no.nav.k9.los.tjenester.avdelingsleder

import no.nav.k9.los.db.TransactionalManager
import no.nav.k9.los.domene.modell.*
import no.nav.k9.los.domene.repository.OppgaveKøRepository
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.kodeverk.*
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import no.nav.k9.los.tjenester.avdelingsleder.oppgaveko.*
import no.nav.k9.los.tjenester.avdelingsleder.reservasjoner.ReservasjonDto
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import no.nav.k9.los.tjenester.saksbehandler.saksliste.OppgavekøDto
import no.nav.k9.los.tjenester.saksbehandler.saksliste.SaksbehandlerDto
import no.nav.k9.los.tjenester.saksbehandler.saksliste.SorteringDto
import java.time.LocalDate
import java.util.*

class AvdelingslederTjeneste(
    private val transactionalManager: TransactionalManager,
    private val oppgaveKøRepository: OppgaveKøRepository,
    private val oppgaveKøV3Repository: OppgaveKoRepository,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val oppgaveTjeneste: OppgaveTjeneste,
    private val pepClient: IPepClient,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste,
) {
    suspend fun hentOppgaveKø(uuid: UUID): OppgavekøDto {
        val oppgaveKø = oppgaveKøRepository.hentOppgavekø(uuid, ignorerSkjerming = false)
        return lagOppgaveKøDto(oppgaveKø)
    }

    suspend fun hentOppgaveKøer(): List<OppgavekøDto> {
        return oppgaveKøRepository.hentAlle().map {
            lagOppgaveKøDto(it)
        }.sortedBy { it.navn }
    }

    private suspend fun lagOppgaveKøDto(oppgaveKø: OppgaveKø) = OppgavekøDto(
        id = oppgaveKø.id,
        navn = oppgaveKø.navn,
        sortering = SorteringDto(
            sorteringType = KøSortering.fraKode(oppgaveKø.sortering.kode),
            fomDato = oppgaveKø.fomDato,
            tomDato = oppgaveKø.tomDato
        ),
        behandlingTyper = oppgaveKø.filtreringBehandlingTyper,
        fagsakYtelseTyper = oppgaveKø.filtreringYtelseTyper,
        andreKriterier = oppgaveKø.filtreringAndreKriterierType,
        sistEndret = oppgaveKø.sistEndret,
        skjermet = oppgaveKø.skjermet,
        antallBehandlinger = oppgaveTjeneste.hentAntallOppgaver(oppgavekøId = oppgaveKø.id, taMedReserverte = true),
        antallUreserverteOppgaver = oppgaveTjeneste.hentAntallOppgaver(
            oppgavekøId = oppgaveKø.id,
            taMedReserverte = false
        ),
        saksbehandlere = oppgaveKø.saksbehandlere,
        kriterier = oppgaveKø.lagKriterier()
    )

    suspend fun opprettOppgaveKø(): IdDto {
        val uuid = UUID.randomUUID()
        oppgaveKøRepository.lagre(uuid) {
            OppgaveKø(
                id = uuid,
                navn = "Ny kø",
                sistEndret = LocalDate.now(),
                sortering = KøSortering.OPPRETT_BEHANDLING,
                filtreringBehandlingTyper = mutableListOf(),
                filtreringYtelseTyper = mutableListOf(),
                filtreringAndreKriterierType = mutableListOf(),
                enhet = Enhet.NASJONAL,
                fomDato = null,
                tomDato = null,
                saksbehandlere = mutableListOf()
            )
        }
        oppgaveKøRepository.oppdaterKøMedOppgaver(uuid)
        return IdDto(uuid.toString())
    }

    suspend fun slettOppgavekø(uuid: UUID) {
        oppgaveKøRepository.slett(uuid)
    }

    // TODO: slett når frontend har begynt å bruke nytt endepunkt
    suspend fun søkSaksbehandler(epostDto: EpostDto): Saksbehandler {
        var saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedEpost(epostDto.epost)
        if (saksbehandler == null) {
            saksbehandler = Saksbehandler(
                null, null, null, epostDto.epost, mutableSetOf(), null
            )
            saksbehandlerRepository.addSaksbehandler(saksbehandler)
        }
        return saksbehandler
    }

    suspend fun leggTilSaksbehandler(epost: String) {
        if (saksbehandlerRepository.finnSaksbehandlerMedEpost(epost) != null) {
            throw IllegalStateException("Saksbehandler finnes fra før")
        }
        // lagrer med tomme verdier, disse blir populert etter at saksbehandleren har logget seg inn
        val saksbehandler = Saksbehandler(null, null, null, epost, mutableSetOf(), null)
        saksbehandlerRepository.addSaksbehandler(saksbehandler)
    }

    suspend fun slettSaksbehandler(
        epost: String,
    ) {
        val skjermet = pepClient.harTilgangTilKode6()

        transactionalManager.transaction { tx ->
            // V3-modellen: Sletter køer saksbehandler er med i
            oppgaveKøV3Repository.hentKoerMedOppgittSaksbehandler(tx, epost, skjermet, true).forEach { kø ->
                oppgaveKøV3Repository.endre(tx, kø.copy(saksbehandlere = kø.saksbehandlere - epost), skjermet)
            }

            // Sletter fra saksbehandler-tabellen
            saksbehandlerRepository.slettSaksbehandler(
                tx,
                epost,
                skjermet
            )
        }

        // V1-modellen: Sletter køer saksbehandler er med i. (Lager sin egen transaksjon.)
        oppgaveKøRepository.hentAlle().forEach { t: OppgaveKø ->
            oppgaveKøRepository.lagre(t.id) { oppgaveKø ->
                oppgaveKø!!.saksbehandlere =
                    oppgaveKø.saksbehandlere.filter { it.epost != epost }
                        .toMutableList()
                oppgaveKø
            }
        }
    }

    suspend fun hentSaksbehandlere(): List<SaksbehandlerDto> {
        val saksbehandlersKoer = hentSaksbehandlersOppgavekoer()
        return saksbehandlersKoer.entries.map {
            SaksbehandlerDto(
                id = it.key.id,
                brukerIdent = it.key.brukerIdent,
                navn = it.key.navn,
                epost = it.key.epost,
                enhet = it.key.enhet,
                oppgavekoer = it.value.map { ko -> ko.navn })
        }.sortedBy { it.navn }
    }

    suspend fun endreBehandlingsTyper(behandling: BehandlingsTypeDto) {
        oppgaveKøRepository.lagre(UUID.fromString(behandling.id)) { oppgaveKø ->
            oppgaveKø!!.filtreringBehandlingTyper =
                behandling.behandlingsTyper.filter { it.checked }
                    .map { it.behandlingType }.toMutableList()
            oppgaveKø
        }
        oppgaveKøRepository.oppdaterKøMedOppgaver(UUID.fromString(behandling.id))
    }

    private suspend fun hentSaksbehandlersOppgavekoer(): Map<Saksbehandler, List<OppgavekøDto>> {
        val koer = oppgaveTjeneste.hentOppgaveKøer()
        val saksbehandlere = saksbehandlerRepository.hentAlleSaksbehandlere()
        val map = mutableMapOf<Saksbehandler, List<OppgavekøDto>>()
        for (saksbehandler in saksbehandlere) {
            map[saksbehandler] = koer.filter { oppgaveKø ->
                oppgaveKø.saksbehandlere
                    .any { s -> s.epost == saksbehandler.epost }
            }
                .map { oppgaveKø ->
                    OppgavekøDto(
                        id = oppgaveKø.id,
                        navn = oppgaveKø.navn,
                        behandlingTyper = oppgaveKø.filtreringBehandlingTyper,
                        fagsakYtelseTyper = oppgaveKø.filtreringYtelseTyper,
                        saksbehandlere = oppgaveKø.saksbehandlere,
                        antallBehandlinger = oppgaveKø.oppgaverOgDatoer.size, //TODO dette feltet i DTO-en brukers annet sted til å sende antall inkludert reserverte, her er det ekskludert reserverte
                        antallUreserverteOppgaver = oppgaveKø.oppgaverOgDatoer.size,
                        sistEndret = oppgaveKø.sistEndret,
                        skjermet = oppgaveKø.skjermet,
                        sortering = SorteringDto(oppgaveKø.sortering, oppgaveKø.fomDato, oppgaveKø.tomDato),
                        andreKriterier = oppgaveKø.filtreringAndreKriterierType,
                        kriterier = oppgaveKø.lagKriterier()
                    )
                }
        }
        return map
    }

    suspend fun endreSkjerming(skjermet: SkjermetDto) {
        oppgaveKøRepository.lagre(UUID.fromString(skjermet.id)) { oppgaveKø ->
            oppgaveKø!!.skjermet = skjermet.skjermet
            oppgaveKø
        }
        oppgaveKøRepository.oppdaterKøMedOppgaver(UUID.fromString(skjermet.id))
    }

    suspend fun endreYtelsesType(ytelse: YtelsesTypeDto) {
        val omsorgsdagerYtelser = listOf(
            FagsakYtelseType.OMSORGSDAGER,
            FagsakYtelseType.OMSORGSPENGER_KS,
            FagsakYtelseType.OMSORGSPENGER_AO,
            FagsakYtelseType.OMSORGSPENGER_MA
        )
        oppgaveKøRepository.lagre(UUID.fromString(ytelse.id)) { oppgaveKø ->
            oppgaveKø!!.filtreringYtelseTyper = mutableListOf()
            if (ytelse.fagsakYtelseType != null) {
                ytelse.fagsakYtelseType.forEach { fagsakYtelseType ->
                    if (fagsakYtelseType == "OMD") {
                        omsorgsdagerYtelser.forEach { oppgaveKø.filtreringYtelseTyper.add(it) }
                    } else {
                        oppgaveKø.filtreringYtelseTyper.add(FagsakYtelseType.fraKode(fagsakYtelseType))
                    }
                }
            }
            oppgaveKø
        }
        oppgaveKøRepository.oppdaterKøMedOppgaver(UUID.fromString(ytelse.id))
    }

    suspend fun endreKriterium(kriteriumDto: AndreKriterierDto) {
        oppgaveKøRepository.lagre(UUID.fromString(kriteriumDto.id)) { oppgaveKø ->
            if (kriteriumDto.checked) {
                oppgaveKø!!.filtreringAndreKriterierType = oppgaveKø.filtreringAndreKriterierType.filter {
                    it.andreKriterierType != kriteriumDto.andreKriterierType
                }.toMutableList()
                oppgaveKø.filtreringAndreKriterierType.add(kriteriumDto)
            } else oppgaveKø!!.filtreringAndreKriterierType = oppgaveKø.filtreringAndreKriterierType.filter {
                it.andreKriterierType != kriteriumDto.andreKriterierType
            }.toMutableList()
            oppgaveKø
        }
        oppgaveKøRepository.oppdaterKøMedOppgaver(UUID.fromString(kriteriumDto.id))
    }

    suspend fun endreOppgavekøNavn(køNavn: OppgavekøNavnDto) {
        oppgaveKøRepository.lagre(UUID.fromString(køNavn.id)) { oppgaveKø ->
            oppgaveKø!!.navn = køNavn.navn
            oppgaveKø
        }
    }

    suspend fun endreKøSortering(køSortering: KøSorteringDto) {
        oppgaveKøRepository.lagre(UUID.fromString(køSortering.id)) { oppgaveKø ->
            oppgaveKø!!.sortering = køSortering.oppgavekoSorteringValg
            oppgaveKø
        }
        oppgaveKøRepository.oppdaterKøMedOppgaver(UUID.fromString(køSortering.id))

    }

    suspend fun endreKøSorteringDato(datoSortering: SorteringDatoDto) {
        oppgaveKøRepository.lagre(UUID.fromString(datoSortering.id)) { oppgaveKø ->
            oppgaveKø!!.fomDato = datoSortering.fomDato
            oppgaveKø.tomDato = datoSortering.tomDato
            oppgaveKø
        }
        oppgaveKøRepository.oppdaterKøMedOppgaver(UUID.fromString(datoSortering.id))
    }

    suspend fun endreKøKriterier(kriteriumDto: KriteriumDto) {
        kriteriumDto.valider()
        oppgaveKøRepository.lagre(UUID.fromString(kriteriumDto.id)) { oppgaveKø ->
            if (kriteriumDto.checked != null && kriteriumDto.checked == false)
                fjernKriterium(kriteriumDto, oppgaveKø!!)
            else leggTilEllerEndreKriterium(kriteriumDto, oppgaveKø!!)
            oppgaveKø
        }
        oppgaveKøRepository.oppdaterKøMedOppgaver(UUID.fromString(kriteriumDto.id))
    }

    private fun leggTilEllerEndreKriterium(kriteriumDto: KriteriumDto, oppgaveKø: OppgaveKø) {
        when (kriteriumDto.kriterierType) {
            KøKriterierType.FEILUTBETALING ->
                oppgaveKø.filtreringFeilutbetaling =
                    Intervall(kriteriumDto.fom?.toLong(), kriteriumDto.tom?.toLong())

            KøKriterierType.MERKNADTYPE -> oppgaveKø.merknadKoder = kriteriumDto.koder ?: emptyList()
            KøKriterierType.OPPGAVEKODE -> oppgaveKø.oppgaveKoder = kriteriumDto.koder ?: emptyList()
            KøKriterierType.NYE_KRAV -> oppgaveKø.nyeKrav = kriteriumDto.inkluder
            KøKriterierType.FRA_ENDRINGSDIALOG -> oppgaveKø.fraEndringsdialog = kriteriumDto.inkluder
            else -> throw IllegalArgumentException("Støtter ikke kriterierType=${kriteriumDto.kriterierType}")
        }
    }

    private fun fjernKriterium(kriteriumDto: KriteriumDto, oppgaveKø: OppgaveKø) {
        when (kriteriumDto.kriterierType) {
            KøKriterierType.FEILUTBETALING -> oppgaveKø.filtreringFeilutbetaling = null
            KøKriterierType.MERKNADTYPE -> oppgaveKø.merknadKoder = emptyList()
            KøKriterierType.OPPGAVEKODE -> oppgaveKø.oppgaveKoder = emptyList()
            KøKriterierType.NYE_KRAV -> oppgaveKø.nyeKrav = null
            KøKriterierType.FRA_ENDRINGSDIALOG -> oppgaveKø.fraEndringsdialog = null
            else -> throw IllegalArgumentException("Støtter ikke fjerning av kriterierType=${kriteriumDto.kriterierType}")
        }
    }

    suspend fun leggFjernSaksbehandlereFraOppgaveKø(saksbehandlereDto: Array<SaksbehandlerOppgavekoDto>) {
        val saksbehandlerKøId = saksbehandlereDto.first().id
        if (!saksbehandlereDto.all { it.id == saksbehandlerKøId }) {
            throw IllegalArgumentException("Støtter ikke å legge til eller fjerne saksbehandlere fra flere køer samtidig")
        }
        val saksbehandlereSomSkalLeggesTil = saksbehandlereDto.filter { it.checked }
            .map { saksbehandlerRepository.finnSaksbehandlerMedEpost(it.epost)!! }
        val saksbehandlereSomSkalFjernes = saksbehandlereDto.filter { !it.checked }
            .map { saksbehandlerRepository.finnSaksbehandlerMedEpost(it.epost)!! }

        oppgaveKøRepository.lagre(UUID.fromString(saksbehandlerKøId)) { oppgaveKø ->
            saksbehandlereSomSkalLeggesTil.forEach { nySaksbehandler ->
                oppgaveKø!!.saksbehandlere.find { it.epost == nySaksbehandler.epost }
                    ?: oppgaveKø.saksbehandlere.add(nySaksbehandler)
            }
            saksbehandlereSomSkalFjernes.forEach { saksbehandlerSomSkalFjernes ->
                oppgaveKø!!.saksbehandlere.removeIf { it.epost == saksbehandlerSomSkalFjernes.epost }
            }
            oppgaveKø!!
        }
    }

    suspend fun leggFjernSaksbehandlerOppgavekø(saksbehandlerKø: SaksbehandlerOppgavekoDto) {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedEpost(
            saksbehandlerKø.epost
        )!!
        oppgaveKøRepository.lagre(UUID.fromString(saksbehandlerKø.id))
        { oppgaveKø ->
            if (saksbehandlerKø.checked && !oppgaveKø!!.saksbehandlere.any { it.epost == saksbehandler.epost }) {
                oppgaveKø.saksbehandlere.add(
                    saksbehandler
                )
            } else oppgaveKø!!.saksbehandlere = oppgaveKø.saksbehandlere.filter {
                it.epost != saksbehandlerKø.epost
            }.toMutableList()
            oppgaveKø
        }
    }

    suspend fun hentAlleAktiveReservasjonerV3(): List<ReservasjonDto> {
        val innloggetBrukerHarKode6Tilgang = pepClient.harTilgangTilKode6()

        return reservasjonV3Tjeneste.hentAlleAktiveReservasjoner().flatMap { reservasjonMedOppgaver ->
            val saksbehandler =
                saksbehandlerRepository.finnSaksbehandlerMedId(reservasjonMedOppgaver.reservasjonV3.reservertAv)!!
            val saksbehandlerHarKode6Tilgang = pepClient.harTilgangTilKode6(saksbehandler.brukerIdent!!)

            if (innloggetBrukerHarKode6Tilgang != saksbehandlerHarKode6Tilgang) {
                emptyList()
            } else {
                if (reservasjonMedOppgaver.oppgaveV1 != null) {
                    listOf(
                        ReservasjonDto(
                            reservertAvEpost = saksbehandler.epost,
                            reservertAvIdent = saksbehandler.brukerIdent!!,
                            reservertAvNavn = saksbehandler.navn,
                            saksnummer = reservasjonMedOppgaver.oppgaveV1.fagsakSaksnummer,
                            journalpostId = reservasjonMedOppgaver.oppgaveV1.journalpostId,
                            behandlingType = reservasjonMedOppgaver.oppgaveV1.behandlingType,
                            reservertTilTidspunkt = reservasjonMedOppgaver.reservasjonV3.gyldigTil,
                            kommentar = reservasjonMedOppgaver.reservasjonV3.kommentar ?: "",
                            tilBeslutter = reservasjonMedOppgaver.oppgaveV1.tilBeslutter,
                            oppgavenøkkel = OppgaveNøkkelDto.forV1Oppgave(reservasjonMedOppgaver.oppgaveV1.eksternId.toString()),
                        )
                    )
                } else {
                    reservasjonMedOppgaver.oppgaverV3.map { oppgave ->
                        ReservasjonDto(
                            reservertAvEpost = saksbehandler.epost,
                            reservertAvIdent = saksbehandler.brukerIdent!!,
                            reservertAvNavn = saksbehandler.navn,
                            saksnummer = oppgave.hentVerdi("saksnummer"), //TODO: Oppgaveagnostisk logikk. Løses antagelig ved å skrive om frontend i dette tilfellet
                            journalpostId = oppgave.hentVerdi("journalpostId"),
                            behandlingType = BehandlingType.fraKode(oppgave.hentVerdi("behandlingTypekode")!!),
                            reservertTilTidspunkt = reservasjonMedOppgaver.reservasjonV3.gyldigTil,
                            kommentar = reservasjonMedOppgaver.reservasjonV3.kommentar ?: "",
                            tilBeslutter = oppgave.hentVerdi("liggerHosBeslutter").toBoolean(),
                            oppgavenøkkel = OppgaveNøkkelDto(oppgave),
                        )
                    }.toList()
                }
            }
        }
    }
}
