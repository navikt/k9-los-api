package no.nav.k9.los.tjenester.saksbehandler.oppgave

import kotlinx.coroutines.runBlocking
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.integrasjon.pdl.IPdlService
import no.nav.k9.los.integrasjon.pdl.fnr
import no.nav.k9.los.integrasjon.pdl.navn
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.reservasjonkonvertering.ReservasjonOversetter
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveKoTjeneste
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Dto
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.GenerellOppgaveV3Dto
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*

class OppgaveApisTjeneste(
    private val oppgaveTjeneste: OppgaveTjeneste,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste,
    private val reservasjonOversetter: ReservasjonOversetter,
    private val oppgaveRepository: no.nav.k9.los.domene.repository.OppgaveRepository,
    private val oppgaveV3Repository: OppgaveRepository,
    private val oppgaveV3RepositoryMedTxWrapper: no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepositoryTxWrapper,
    private val oppgaveKoRepository: OppgaveKoRepository,
    private val oppgaveKoTjeneste: OppgaveKoTjeneste,
    private val transactionalManager: TransactionalManager,
    private val pdlService: IPdlService,
) {

    suspend fun reserverOppgave(
        innloggetBruker: Saksbehandler,
        oppgaveIdMedOverstyring: OppgaveIdMedOverstyring
    ): OppgaveStatusDto {
        val reserverFra = LocalDateTime.now()
        /*
         1. Reserver i V1-modellen
         2. Reserver i V3-modellen
         3. Returner status fra V3.
         -- V1 er i praksis en skyggekopi for sikring av evt rollback
         */

        // Fjernes når V1 skal vekk
        oppgaveTjeneste.reserverOppgave(
            innloggetBruker.brukerIdent!!,
            oppgaveIdMedOverstyring.overstyrIdent,
            UUID.fromString(oppgaveIdMedOverstyring.oppgaveNøkkel.oppgaveEksternId),
            oppgaveIdMedOverstyring.overstyrSjekk,
            oppgaveIdMedOverstyring.overstyrBegrunnelse
        )

        val oppgaveV3 = transactionalManager.transaction { tx ->
            oppgaveV3Repository.hentNyesteOppgaveForEksternId(tx, oppgaveIdMedOverstyring.oppgaveNøkkel.oppgaveEksternId)
        }

        val reserverForIdent = oppgaveIdMedOverstyring.overstyrIdent ?: innloggetBruker.brukerIdent
        val reserverForSaksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(reserverForIdent!!)!!
        val reservasjonV3 = reservasjonV3Tjeneste.forsøkReservasjonOgReturnerAktiv(
            reservasjonsnøkkel = oppgaveV3.reservasjonsnøkkel,
            reserverForId = reserverForSaksbehandler.id!!,
            gyldigFra = reserverFra,
            utføresAvId = innloggetBruker.id!!,
            kommentar = oppgaveIdMedOverstyring.overstyrBegrunnelse ?: "",
            gyldigTil = reserverFra.plusHours(24).forskyvReservasjonsDato()
        )
        //TODO: sjekke statusobjekt, saksbehandler som holder reservasjon -- feks conflict hvis noen andre hadde reservasjon fra før
        val saksbehandlerSomHarReservasjon =
            saksbehandlerRepository.finnSaksbehandlerMedId(reservasjonV3.reservertAv)
        val oppgaveStatusDto = OppgaveStatusDto(
            erReservert = true,
            reservertTilTidspunkt = reservasjonV3.gyldigTil,
            erReservertAvInnloggetBruker = reservasjonV3.reservertAv == innloggetBruker.id!!,
            reservertAv = saksbehandlerSomHarReservasjon.brukerIdent,
            reservertAvNavn = saksbehandlerSomHarReservasjon.navn,
            flyttetReservasjon = null,
            kanOverstyres = reservasjonV3.reservertAv != innloggetBruker.id!!
        )
        return oppgaveStatusDto
    }

    suspend fun endreReservasjon(
        reservasjonEndringDto: ReservasjonEndringDto,
        innloggetBruker: Saksbehandler
    ): ReservasjonV3Dto {
        // Fjernes når V1 skal vekk
        oppgaveTjeneste.endreReservasjonPåOppgave(reservasjonEndringDto)

        val tilSaksbehandler =
            reservasjonEndringDto.brukerIdent?.let { saksbehandlerRepository.finnSaksbehandlerMedIdent(it) }

        val oppgave =
            oppgaveV3RepositoryMedTxWrapper.hentOppgave(reservasjonEndringDto.oppgaveNøkkel.oppgaveEksternId) //TODO oppgaveId er behandlingsUUID?
        val nyReservasjon =
            reservasjonV3Tjeneste.endreReservasjon(
                reservasjonsnøkkel = oppgave.reservasjonsnøkkel,
                endretAvBrukerId = innloggetBruker.id!!,
                nyTildato = reservasjonEndringDto.reserverTil?.let {
                    LocalDateTime.of(
                        reservasjonEndringDto.reserverTil,
                        LocalTime.MAX
                    )
                },
                nySaksbehandlerId = tilSaksbehandler?.id,
                kommentar = reservasjonEndringDto.begrunnelse
            )

        val reservertAv = saksbehandlerRepository.finnSaksbehandlerMedId(nyReservasjon!!.reservertAv)

        return byggReservasjonV3Dto(nyReservasjon, reservertAv)
    }

    fun forlengReservasjon(
        forlengReservasjonDto: ForlengReservasjonDto,
        innloggetBruker: Saksbehandler
    ): ReservasjonV3Dto {
        // Fjernes når V1 skal vekk
        oppgaveTjeneste.forlengReservasjonPåOppgave(UUID.fromString(forlengReservasjonDto.oppgaveNøkkel.oppgaveEksternId))

        //TODO oppgaveId er behandlingsUUID?
        val oppgave = oppgaveV3RepositoryMedTxWrapper.hentOppgave(forlengReservasjonDto.oppgaveNøkkel.oppgaveEksternId)
        //TODO: Oppgavetype som ikke er støttet i V3 -- utlede reservasjonsnøkkel

        val forlengetReservasjon =
            reservasjonV3Tjeneste.forlengReservasjon(
                reservasjonsnøkkel = oppgave.reservasjonsnøkkel,
                nyTildato = forlengReservasjonDto.nyTilDato,
                utførtAvBrukerId = innloggetBruker.id!!,
                kommentar = forlengReservasjonDto.kommentar ?: ""
            )

        val reservertAv = saksbehandlerRepository.finnSaksbehandlerMedId(forlengetReservasjon!!.reservertAv)!!

        return byggReservasjonV3Dto(forlengetReservasjon, reservertAv)
    }

    suspend fun overførReservasjon(
        params: FlyttReservasjonId,
        innloggetBruker: Saksbehandler
    ): ReservasjonV3Dto {
        // Fjernes når V1 skal vekk
        oppgaveTjeneste.flyttReservasjon(
            UUID.fromString(params.oppgaveNøkkel.oppgaveEksternId),
            params.brukerIdent,
            params.begrunnelse
        )

        val tilSaksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(
            params.brukerIdent
        )!!

        val oppgave = oppgaveV3RepositoryMedTxWrapper.hentOppgave(params.oppgaveNøkkel.oppgaveEksternId)
        //TODO: Oppgavetype som ikke er støttet i V3 -- utlede reservasjonsnøkkel

        val nyReservasjon = reservasjonV3Tjeneste.overførReservasjon(
            reservasjonsnøkkel = oppgave.reservasjonsnøkkel,
            reserverTil = LocalDateTime.now().plusHours(24).forskyvReservasjonsDato(),
            tilSaksbehandlerId = tilSaksbehandler.id!!,
            utførtAvBrukerId = innloggetBruker.id!!,
            kommentar = params.begrunnelse,
        )

        return byggReservasjonV3Dto(nyReservasjon, tilSaksbehandler)
    }

    suspend fun annullerReservasjon(
        params: OpphevReservasjonId,
        innloggetBruker: Saksbehandler
    ) {
        // Fjernes når V1 skal vekk
        oppgaveTjeneste.frigiReservasjon(UUID.fromString(params.oppgaveNøkkel.oppgaveEksternId), params.begrunnelse)

        val oppgave = oppgaveV3RepositoryMedTxWrapper.hentOppgave(params.oppgaveNøkkel.oppgaveEksternId)
        reservasjonV3Tjeneste.annullerReservasjon(
            oppgave.reservasjonsnøkkel,
            params.begrunnelse,
            innloggetBruker.id!!
        )
    }

    fun hentReserverteOppgaverForSaksbehandler(saksbehandler: Saksbehandler): List<ReservasjonV3Dto> {
        val reservasjoner =
            reservasjonV3Tjeneste.hentReservasjonerForSaksbehandler(saksbehandler.id!!)

        return reservasjoner.map { reservasjon ->
            byggReservasjonV3Dto(reservasjon, saksbehandler)
        }
    }

    fun byggReservasjonV3Dto(
        reservasjon: ReservasjonV3,
        saksbehandler: Saksbehandler
    ): ReservasjonV3Dto {
        // Fjernes når V1 skal vekk
        val oppgaveV1 = reservasjonOversetter.hentV1OppgaveFraReservasjon(reservasjon)
        return if (oppgaveV1 == null) {
            val oppgaverForReservasjonsnøkkel =
                oppgaveV3RepositoryMedTxWrapper.hentÅpneOppgaverForReservasjonsnøkkel(reservasjon.reservasjonsnøkkel)

            val oppgaveV3Dtos = oppgaverForReservasjonsnøkkel.map { oppgave ->
                val person = runBlocking {
                    pdlService.person(oppgave.hentVerdi("aktorId")!!)
                }.person!!
                GenerellOppgaveV3Dto(oppgave, person)
            }
            ReservasjonV3Dto(reservasjon, oppgaveV3Dtos, saksbehandler)
        } else {
            val person = runBlocking {
                pdlService.person(oppgaveV1.aktorId)
            }
            val oppgaveV1Dto = OppgaveDto(
                status = OppgaveStatusDto(
                    true,
                    reservasjon.gyldigTil,
                    true,
                    saksbehandler.brukerIdent,
                    saksbehandler?.navn,
                    flyttetReservasjon = null
                ),
                behandlingId = oppgaveV1.behandlingId,
                saksnummer = oppgaveV1.fagsakSaksnummer,
                journalpostId = oppgaveV1.journalpostId,
                navn = person.person!!.navn(),
                system = oppgaveV1.system,
                personnummer = person.person!!.fnr(),
                behandlingstype = oppgaveV1.behandlingType,
                fagsakYtelseType = oppgaveV1.fagsakYtelseType,
                behandlingStatus = oppgaveV1.behandlingStatus,
                erTilSaksbehandling = true,
                opprettetTidspunkt = oppgaveV1.behandlingOpprettet,
                behandlingsfrist = oppgaveV1.behandlingsfrist,
                eksternId = oppgaveV1.eksternId,
                tilBeslutter = oppgaveV1.tilBeslutter,
                utbetalingTilBruker = oppgaveV1.utbetalingTilBruker,
                selvstendigFrilans = oppgaveV1.selvstendigFrilans,
                søktGradering = oppgaveV1.søktGradering,
                avklarArbeidsforhold = oppgaveV1.avklarArbeidsforhold,
                merknad = oppgaveTjeneste.hentAktivMerknad(oppgaveV1.eksternId.toString())
            )
            ReservasjonV3Dto(reservasjon, oppgaveV1Dto, saksbehandler)
        }
    }
}