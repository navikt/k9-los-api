package no.nav.k9.los.nyoppgavestyring.mottak.reservasjon

import kotlinx.coroutines.runBlocking
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
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

        val innloggetBruker =
            saksbehandlerRepository.finnSaksbehandlerMedIdent(azureGraphService.hentIdentTilInnloggetBruker())!!

        val saksbehandlerSomVilReservere =
            saksbehandlerRepository.finnSaksbehandlerMedEpost(taReservasjonDto.saksbehandlerEpost)!!

        return transactionalManager.transaction { tx ->
            val aktivReservasjon =
                reservasjonV3Repository.hentAktivReservasjonForReservasjonsnøkkel(
                    taReservasjonDto.reservasjonsnøkkel,
                    tx
                )

            if (aktivReservasjon != null) {
                val saksbehandlerSomHarReservert =
                    runBlocking { saksbehandlerRepository.finnSaksbehandlerMedId(aktivReservasjon.reservertAv) }

                if (saksbehandlerSomVilReservere.epost == saksbehandlerSomHarReservert.epost) {
                    if (aktivReservasjon.gyldigTil < taReservasjonDto.gyldigTil) {
                        reservasjonV3Repository.lagreReservasjon( //forlenge reservasjon
                            ReservasjonV3(
                                saksbehandlerSomVilReservere,
                                taReservasjonDto.copy(gyldigFra = aktivReservasjon.gyldigTil)
                            ),
                            tx
                        )
                        return@transaction ReservasjonStatusDto(
                            reservasjonsnøkkel = taReservasjonDto.reservasjonsnøkkel,
                            gyldigFra = aktivReservasjon.gyldigFra,
                            gyldigTil = taReservasjonDto.gyldigTil,
                            saksbehandlerSomHarReservasjon = saksbehandlerSomVilReservere,
                            innloggetBruker = innloggetBruker,
                        )
                    } else { //allerede reservert lengre enn ønsket
                        return@transaction ReservasjonStatusDto(
                            reservasjonsnøkkel = taReservasjonDto.reservasjonsnøkkel,
                            aktivReservasjon.gyldigFra,
                            aktivReservasjon.gyldigTil,
                            saksbehandlerSomHarReservert,
                            innloggetBruker
                        )
                    }
                } else { //reservert av andre
                    return@transaction ReservasjonStatusDto(
                        reservasjonsnøkkel = taReservasjonDto.reservasjonsnøkkel,
                        aktivReservasjon.gyldigFra,
                        aktivReservasjon.gyldigTil,
                        saksbehandlerSomHarReservert,
                        innloggetBruker
                    )
                }
            } else {
                val reservasjonTilLagring = ReservasjonV3(
                    reservertAv = saksbehandlerSomVilReservere.id!!,
                    reservasjonsnøkkel = taReservasjonDto.reservasjonsnøkkel,
                    gyldigFra = taReservasjonDto.gyldigFra,
                    gyldigTil = taReservasjonDto.gyldigTil,
                )
                reservasjonV3Repository.lagreReservasjon(reservasjonTilLagring, tx)

                return@transaction ReservasjonStatusDto(
                    reservasjonsnøkkel = taReservasjonDto.reservasjonsnøkkel,
                    taReservasjonDto.gyldigFra,
                    taReservasjonDto.gyldigTil,
                    saksbehandlerSomVilReservere,
                    innloggetBruker
                )
            }
        }
    }


    fun annullerReservasjon(annullerReservasjonDto: AnnullerReservasjonDto) {
        val innloggetBruker =
            runBlocking { saksbehandlerRepository.finnSaksbehandlerMedIdent(azureGraphService.hentIdentTilInnloggetBruker()) } !!
        transactionalManager.transaction { tx ->
            val saksbehandler = runBlocking { saksbehandlerRepository.finnSaksbehandlerMedEpost(annullerReservasjonDto.reservertAv) }!!
            val annullertReservasjonId = reservasjonV3Repository.annullerAktivReservasjon(
                saksbehandler,
                annullerReservasjonDto.reservasjonsnøkkel,
                tx
            )
            reservasjonV3Repository.lagreEndring(
                ReservasjonV3Endring(
                    annullertReservasjonId = annullertReservasjonId,
                    nyReservasjonId = null,
                    endretAv = innloggetBruker.id!!,
                ), tx
            )
        }
    }

    fun overførReservasjon(overførReservasjonDto: OverførReservasjonDto) {
        val overføringstidspunkt = LocalDateTime.now()
        val innloggetBruker =
            runBlocking { saksbehandlerRepository.finnSaksbehandlerMedEpost(azureGraphService.hentIdentTilInnloggetBruker()) }!!

        val saksbehandlerSomSkalFåReservasjon =
            runBlocking { saksbehandlerRepository.finnSaksbehandlerMedEpost(overførReservasjonDto.tilSaksbehandlerEpost) }
                ?: throw IllegalArgumentException("Saksbehandler ${overførReservasjonDto.tilSaksbehandlerEpost} finnes ikke!")

        transactionalManager.transaction { tx ->
            val annullertReservasjonId = reservasjonV3Repository.annullerAktivReservasjon(
                saksbehandler = runBlocking { saksbehandlerRepository.finnSaksbehandlerMedEpost(overførReservasjonDto.fraSaksbehandlerEpost)!! },
                reservasjonsnøkkel = overførReservasjonDto.reservasjonsnøkkel,
                tx
            )
            val nyReservasjonId = reservasjonV3Repository.lagreReservasjon(
                ReservasjonV3(
                    reservertAv = saksbehandlerSomSkalFåReservasjon.id!!,
                    reservasjonsnøkkel = overførReservasjonDto.reservasjonsnøkkel,
                    gyldigFra = overføringstidspunkt,
                    gyldigTil = overførReservasjonDto.reserverTil
                ), tx
            )
            reservasjonV3Repository.lagreEndring(
                ReservasjonV3Endring(
                    annullertReservasjonId = annullertReservasjonId,
                    nyReservasjonId = nyReservasjonId,
                    endretAv = innloggetBruker.id!!,
                ), tx
            )
        }
    }
}