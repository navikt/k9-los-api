package no.nav.k9.los.nyoppgavestyring.mottak.reservasjon

import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import java.time.LocalDateTime

class ReservasjonV3Tjeneste(
    private val transactionalManager: TransactionalManager,
    private val reservasjonV3Repository: ReservasjonV3Repository,
) {
    fun taReservasjon(taReservasjonDto: TaReservasjonDto) {
        transactionalManager.transaction { tx ->
            val reservasjonTilLagring = ReservasjonV3(
                saksbehandlerEpost = taReservasjonDto.saksbehandlerEpost,
                reservasjonsnøkkel = taReservasjonDto.reservasjonsnøkkel,
                gyldigFra = taReservasjonDto.gyldigFra,
                gyldigTil = taReservasjonDto.gyldigTil,
            )
            reservasjonV3Repository.lagreReservasjon(reservasjonTilLagring, tx)
        }
    }

    fun annullerReservasjon(annullerReservasjonDto: AnnullerReservasjonDto) {
        transactionalManager.transaction { tx ->
            reservasjonV3Repository.annullerAktivReservasjon(annullerReservasjonDto, tx)
        }
    }

    fun overførReservasjon(overførReservasjonDto: OverførReservasjonDto) {
        transactionalManager.transaction { tx ->
            reservasjonV3Repository.annullerAktivReservasjon(
                AnnullerReservasjonDto(
                    overførReservasjonDto.fraSaksbehandlerEpost,
                    overførReservasjonDto.reservasjonsnøkkel
                ), tx
            )
            reservasjonV3Repository.lagreReservasjon(
                ReservasjonV3(
                    saksbehandlerEpost = overførReservasjonDto.tilSaksbehandlerEpost,
                    reservasjonsnøkkel = overførReservasjonDto.reservasjonsnøkkel,
                    gyldigFra = LocalDateTime.now(),
                    gyldigTil = overførReservasjonDto.reserverTil
                ), tx
            )
        }
    }
}