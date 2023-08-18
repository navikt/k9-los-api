package no.nav.k9.los.nyoppgavestyring.mottak.reservasjon

import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.azuregraph.IAzureGraphService
import java.time.LocalDateTime

class ReservasjonV3Tjeneste(
    private val transactionalManager: TransactionalManager,
    private val reservasjonV3Repository: ReservasjonV3Repository,
    private val pepClient: IPepClient,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val azureGraphService: IAzureGraphService,
) {
    suspend fun taReservasjon(taReservasjonDto: TaReservasjonDto): ReservasjonStatusDto {
        if (!pepClient.harTilgangTilReservingAvOppgaver()) {
            return ReservasjonStatusDto.blankIkkeTilgang()
        }

        val identTilInnloggetBruker = azureGraphService.hentIdentTilInnloggetBruker()

        val innloggetBruker =
            saksbehandlerRepository.finnSaksbehandlerMedIdent(identTilInnloggetBruker)!!

        val saksbehandlerSomSkalHaReservasjon =
            saksbehandlerRepository.finnSaksbehandlerMedEpost(taReservasjonDto.saksbehandlerEpost)!!

        return transactionalManager.transaction { tx ->
            sjekkOgHåndterEksisterendeReservasjon(taReservasjonDto, saksbehandlerSomSkalHaReservasjon, innloggetBruker, tx)
        }
    }

    private fun sjekkOgHåndterEksisterendeReservasjon(
        taReservasjonDto: TaReservasjonDto,
        saksbehandlerSomVilReservere: Saksbehandler,
        innloggetBruker: Saksbehandler,
        tx: TransactionalSession
    ): ReservasjonStatusDto {
        val aktivReservasjon =
            reservasjonV3Repository.hentAktivReservasjonForReservasjonsnøkkel(
                taReservasjonDto.reservasjonsnøkkel,
                tx
            )

        if (aktivReservasjon == null) {
            val reservasjonTilLagring = ReservasjonV3(
                reservertAv = saksbehandlerSomVilReservere.id!!,
                reservasjonsnøkkel = taReservasjonDto.reservasjonsnøkkel,
                gyldigFra = taReservasjonDto.gyldigFra,
                gyldigTil = taReservasjonDto.gyldigTil,
            )
            reservasjonV3Repository.lagreReservasjon(reservasjonTilLagring, tx)

            return ReservasjonStatusDto(
                reservasjonsnøkkel = taReservasjonDto.reservasjonsnøkkel,
                taReservasjonDto.gyldigFra,
                taReservasjonDto.gyldigTil,
                saksbehandlerSomVilReservere,
                innloggetBruker
            )
        }

        val saksbehandlerSomHarReservert =
            runBlocking { saksbehandlerRepository.finnSaksbehandlerMedId(aktivReservasjon.reservertAv) }

        if (saksbehandlerSomVilReservere.epost != saksbehandlerSomHarReservert.epost) { // reservert av andre
            return ReservasjonStatusDto(
                reservasjonsnøkkel = taReservasjonDto.reservasjonsnøkkel,
                gyldigFra = aktivReservasjon.gyldigFra,
                gyldigTil = aktivReservasjon.gyldigTil,
                saksbehandlerSomHarReservasjon = saksbehandlerSomHarReservert,
                innloggetBruker = innloggetBruker
            )
        }

        if (aktivReservasjon.gyldigTil < taReservasjonDto.gyldigTil) {
            reservasjonV3Repository.lagreReservasjon( //forlenge reservasjon  //TODO: heller annullere gammel reservasjon
                ReservasjonV3(
                    saksbehandlerSomVilReservere,
                    taReservasjonDto.copy(gyldigFra = aktivReservasjon.gyldigTil)
                ),
                tx
            )
            return ReservasjonStatusDto(
                reservasjonsnøkkel = taReservasjonDto.reservasjonsnøkkel,
                gyldigFra = aktivReservasjon.gyldigFra,
                gyldigTil = taReservasjonDto.gyldigTil,
                saksbehandlerSomHarReservasjon = saksbehandlerSomVilReservere,
                innloggetBruker = innloggetBruker,
            )
        } else { //allerede reservert lengre enn ønsket //TODO: kort ned reservasjon i stedet? Avklaring neste uke. Sjekke opp mot V1-logikken
            return ReservasjonStatusDto(
                reservasjonsnøkkel = taReservasjonDto.reservasjonsnøkkel,
                gyldigFra = aktivReservasjon.gyldigFra,
                gyldigTil = aktivReservasjon.gyldigTil,
                saksbehandlerSomHarReservasjon = saksbehandlerSomHarReservert,
                innloggetBruker = innloggetBruker
            )
        }
    }

    fun hentReservasjonerForSaksbehandlerEpost(saksbehandlerEpost: String): List<ReservasjonStatusDto> {
        val identTilInnloggetBruker = runBlocking { azureGraphService.hentIdentTilInnloggetBruker() }

        val innloggetBruker = runBlocking {
            saksbehandlerRepository.finnSaksbehandlerMedIdent(identTilInnloggetBruker)!!
        }
        val saksbehandler = runBlocking { saksbehandlerRepository.finnSaksbehandlerMedEpost(saksbehandlerEpost) }!!
        return transactionalManager.transaction { tx ->
            val aktiveReservasjonerForSaksbehandler =
                reservasjonV3Repository.hentAktiveReservasjonerForSaksbehandler(saksbehandler, tx)
            aktiveReservasjonerForSaksbehandler.map { reservasjon ->
                ReservasjonStatusDto(
                    reservasjonsnøkkel = reservasjon.reservasjonsnøkkel,
                    gyldigFra = reservasjon.gyldigFra,
                    gyldigTil = reservasjon.gyldigTil,
                    saksbehandlerSomHarReservasjon = saksbehandlerRepository.finnSaksbehandlerMedId(reservasjon.reservertAv),
                    innloggetBruker = innloggetBruker
                )
            }
        }
    }


    fun annullerReservasjon(annullerReservasjonDto: AnnullerReservasjonDto): ReservasjonStatusDto {
        val innloggetBruker =
            runBlocking { saksbehandlerRepository.finnSaksbehandlerMedIdent(azureGraphService.hentIdentTilInnloggetBruker()) }!!
        val saksbehandler =
            runBlocking { saksbehandlerRepository.finnSaksbehandlerMedEpost(annullerReservasjonDto.reservertAv) }!!

        transactionalManager.transaction { tx ->
            reservasjonV3Repository.annullerAktivReservasjonOgLagreEndring(saksbehandler, innloggetBruker, annullerReservasjonDto.reservasjonsnøkkel, tx)
        }
        return ReservasjonStatusDto.annullertReservasjon(annullerReservasjonDto.reservasjonsnøkkel)
    }

    fun overførReservasjon(overførReservasjonDto: OverførReservasjonDto) {
        val innloggetBruker =
            runBlocking { saksbehandlerRepository.finnSaksbehandlerMedEpost(azureGraphService.hentIdentTilInnloggetBruker()) }!!

        val saksbehandlerSomSkalFåReservasjon =
            runBlocking { saksbehandlerRepository.finnSaksbehandlerMedEpost(overførReservasjonDto.tilSaksbehandlerEpost) }
                ?: throw IllegalArgumentException("Saksbehandler ${overførReservasjonDto.tilSaksbehandlerEpost} finnes ikke!")

        val saksbehandlerSomHarReservasjon =  runBlocking { saksbehandlerRepository.finnSaksbehandlerMedEpost(overførReservasjonDto.fraSaksbehandlerEpost)!! }

        transactionalManager.transaction { tx ->
            reservasjonV3Repository.overførReservasjon(
                saksbehandlerSomHarReservasjon = saksbehandlerSomHarReservasjon,
                saksbehandlerSomSkalHaReservasjon = saksbehandlerSomSkalFåReservasjon,
                innloggetBruker = innloggetBruker,
                reserverTil = overførReservasjonDto.reserverTil,
                reservasjonsnøkkel = overførReservasjonDto.reservasjonsnøkkel,
                tx
            )
        }
    }
}