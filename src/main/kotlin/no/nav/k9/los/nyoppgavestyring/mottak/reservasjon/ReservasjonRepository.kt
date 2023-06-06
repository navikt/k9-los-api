package no.nav.k9.los.nyoppgavestyring.mottak.reservasjon

import kotliquery.TransactionalSession
import kotliquery.queryOf
import javax.sql.DataSource

class ReservasjonRepository(
    private val dataSource: DataSource,
) {
    fun lagreReservasjon(reservasjon: Reservasjon, tx: TransactionalSession) {
        tx.run(
            queryOf(
                """
                    insert into RESERVASJON_V3(saksbehandler_epost, reservasjonsnøkkel, gyldig_til)
                    values (:saksbehandler, :nøkkel, :gyldig_til)
                """.trimIndent(),
                mapOf(
                    "saksbehandler" to reservasjon.saksbehandlerEpost,
                    "reservasjonsnøkkel" to reservasjon.reservasjonsnøkkel,
                    "gyldig_til" to reservasjon.gyldigTil
                )
            ).asUpdate
        )
    }
}