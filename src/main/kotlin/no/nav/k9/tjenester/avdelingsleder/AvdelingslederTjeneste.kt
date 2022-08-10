package no.nav.k9.tjenester.avdelingsleder

import no.nav.k9.Configuration
import no.nav.k9.KoinProfile
import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.lager.oppgave.Reservasjon
import no.nav.k9.domene.modell.Enhet
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.modell.Intervall
import no.nav.k9.domene.modell.KøKriterierType
import no.nav.k9.domene.modell.KøSortering
import no.nav.k9.domene.modell.OppgaveKø
import no.nav.k9.domene.modell.Saksbehandler
import no.nav.k9.domene.repository.OppgaveKøRepository
import no.nav.k9.domene.repository.OppgaveRepository
import no.nav.k9.domene.repository.ReservasjonRepository
import no.nav.k9.domene.repository.SaksbehandlerRepository
import no.nav.k9.integrasjon.abac.IPepClient
import no.nav.k9.tjenester.avdelingsleder.oppgaveko.AndreKriterierDto
import no.nav.k9.tjenester.avdelingsleder.oppgaveko.BehandlingsTypeDto
import no.nav.k9.tjenester.avdelingsleder.oppgaveko.IdDto
import no.nav.k9.tjenester.avdelingsleder.oppgaveko.KriteriumDto
import no.nav.k9.tjenester.avdelingsleder.oppgaveko.KøSorteringDto
import no.nav.k9.tjenester.avdelingsleder.oppgaveko.OppgavekøNavnDto
import no.nav.k9.tjenester.avdelingsleder.oppgaveko.SaksbehandlerOppgavekoDto
import no.nav.k9.tjenester.avdelingsleder.oppgaveko.SkjermetDto
import no.nav.k9.tjenester.avdelingsleder.oppgaveko.SorteringDatoDto
import no.nav.k9.tjenester.avdelingsleder.oppgaveko.YtelsesTypeDto
import no.nav.k9.tjenester.avdelingsleder.reservasjoner.ReservasjonDto
import no.nav.k9.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import no.nav.k9.tjenester.saksbehandler.saksliste.OppgavekøDto
import no.nav.k9.tjenester.saksbehandler.saksliste.SaksbehandlerDto
import no.nav.k9.tjenester.saksbehandler.saksliste.SorteringDto
import java.time.LocalDate
import java.util.*

class AvdelingslederTjeneste(
    private val oppgaveKøRepository: OppgaveKøRepository,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val oppgaveTjeneste: OppgaveTjeneste,
    private val reservasjonRepository: ReservasjonRepository,
    private val oppgaveRepository: OppgaveRepository,
    private val pepClient: IPepClient,
    private val configuration: Configuration
) {
    fun hentOppgaveKø(uuid: UUID): OppgavekøDto {
        val oppgaveKø = oppgaveKøRepository.hentOppgavekø(uuid)
        return lagOppgaveKøDto(oppgaveKø)
    }

    suspend fun hentOppgaveKøer(): List<OppgavekøDto> {
        if (!erOppgaveStyrer()) {
            return emptyList()
        }
        return oppgaveKøRepository.hent().map {
            lagOppgaveKøDto(it)
        }.sortedBy { it.navn }
    }

    private fun lagOppgaveKøDto(oppgaveKø: OppgaveKø) = OppgavekøDto(
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
        saksbehandlere = oppgaveKø.saksbehandlere,
        kriterier = oppgaveKø.lagKriterier()
    )

    private suspend fun erOppgaveStyrer() = (pepClient.erOppgaveStyrer())

    suspend fun opprettOppgaveKø(): IdDto {
        if (!erOppgaveStyrer()) {
            return IdDto(UUID.randomUUID().toString())
        }

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
        if (!erOppgaveStyrer()) {
            return
        }
        oppgaveKøRepository.slett(uuid)
    }

    suspend fun søkSaksbehandler(epostDto: EpostDto): Saksbehandler {
        var saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedEpost(epostDto.epost)
        if (saksbehandler == null) {
            saksbehandler = Saksbehandler(
                null, null, epostDto.epost, mutableSetOf(), null
            )
            saksbehandlerRepository.addSaksbehandler(saksbehandler)
        }
        return saksbehandler
    }

    suspend fun fjernSaksbehandler(epost: String) {
        saksbehandlerRepository.slettSaksbehandler(epost)
        oppgaveKøRepository.hent().forEach { t: OppgaveKø ->
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
                it.key.brukerIdent,
                it.key.navn,
                it.key.epost,
                it.value.map { ko -> ko.navn })
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
                        antallBehandlinger = oppgaveKø.oppgaverOgDatoer.size,
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
        oppgaveKøRepository.lagre(UUID.fromString(kriteriumDto.id))
        { oppgaveKø ->
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
                oppgaveKø.filtreringFeilutbetaling = Intervall(kriteriumDto.fom?.toLong(), kriteriumDto.tom?.toLong())
            KøKriterierType.MERKNADTYPE ->
                oppgaveKø.merknadKoder = kriteriumDto.koder ?: emptyList()
            else -> throw IllegalArgumentException("Støtter ikke kriterierType=${kriteriumDto.kriterierType}")
        }
    }

    private fun fjernKriterium(kriteriumDto: KriteriumDto, oppgaveKø: OppgaveKø) {
        when (kriteriumDto.kriterierType) {
            KøKriterierType.FEILUTBETALING -> oppgaveKø.filtreringFeilutbetaling = null
            KøKriterierType.MERKNADTYPE -> oppgaveKø.merknadKoder = emptyList()
            else -> throw IllegalArgumentException("Støtter ikke fjerning av kriterierType=${kriteriumDto.kriterierType}")
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

    suspend fun hentAlleReservasjoner(): List<ReservasjonDto> {
        val list = mutableListOf<ReservasjonDto>()
        for (saksbehandler in saksbehandlerRepository.hentAlleSaksbehandlere()) {
            for (uuid in saksbehandler.reservasjoner) {

                val oppgave = oppgaveRepository.hent(uuid)

                if (configuration.koinProfile() != KoinProfile.LOCAL &&
                    !harFagsaksNummerOgHarTilgangTilLesSak(oppgave)
                ) {
                    continue
                }
                val reservasjon = reservasjonRepository.hent(uuid)
                if (reservasjon.reservertTil == null) {
                    continue
                }
                list.add(
                    ReservasjonDto(
                        reservertAvUid = saksbehandler.brukerIdent ?: "",
                        reservertAvNavn = saksbehandler.navn ?: "",
                        reservertTilTidspunkt = reservasjon.reservertTil!!,
                        oppgaveId = reservasjon.oppgave,
                        saksnummer = oppgave.fagsakSaksnummer,
                        behandlingType = oppgave.behandlingType,
                        tilBeslutter = oppgave.tilBeslutter
                    )
                )

            }
        }

        return list.sortedWith(compareBy({ it.reservertAvNavn }, { it.reservertTilTidspunkt }))
    }

    private suspend fun harFagsaksNummerOgHarTilgangTilLesSak(oppgave: Oppgave) : Boolean {
        return oppgave.harFagSaksNummer() && pepClient.harTilgangTilLesSak(
            fagsakNummer = oppgave.fagsakSaksnummer,
            aktørid = oppgave.aktorId
        )
    }

    suspend fun opphevReservasjon(uuid: UUID): Reservasjon {
        val reservasjon = reservasjonRepository.lagre(uuid, true) {
            it!!.begrunnelse = "Opphevet av en avdelingsleder"
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


}
