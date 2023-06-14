package no.nav.k9.los.nyoppgavestyring.mottak.reservasjon

import kotliquery.TransactionalSession
import kotliquery.queryOf
import org.postgresql.util.PSQLException
import java.time.LocalDateTime
import javax.sql.DataSource

class ReservasjonV3Repository(
    private val dataSource: DataSource,
) {
    fun lagreReservasjon(reservasjonV3: ReservasjonV3, tx: TransactionalSession) {
        try {
            tx.run(

                queryOf(
                    """
                    insert into RESERVASJON_V3(saksbehandler_epost, reservasjonsnokkel, gyldig_tidsrom)
                    values (:saksbehandler, :nokkel, tsrange(:gyldig_fra, :gyldig_til))
                """.trimIndent(),
                    mapOf(
                        "saksbehandler" to reservasjonV3.saksbehandlerEpost,
                        "nokkel" to reservasjonV3.reservasjonsnøkkel,
                        "gyldig_fra" to reservasjonV3.gyldigFra,
                        "gyldig_til" to reservasjonV3.gyldigTil
                    )
                ).asUpdate
            )
        } catch (e: PSQLException) {
            if (e.sqlState == "23P01") {//exclusion_violation
                throw IllegalArgumentException("${reservasjonV3.reservasjonsnøkkel} er allerede reservert!")
            } else {
                throw e
            }
        }
    }

    fun annullerAktivReservasjon(annullerReservasjonDto: AnnullerReservasjonDto, tx: TransactionalSession) {
        tx.run(
            queryOf(
                """
                    UPDATE public.reservasjon_v3
                    SET annullert_for_utlop = true, sist_endret = :now
                    WHERE saksbehandler_epost = :saksbehandler_epost
                    and reservasjonsnokkel = :reservasjonsnokkel
                    and upper(gyldig_tidsrom) > localtimestamp
                    and annullert_for_utlop = false
                    """.trimIndent(),
                mapOf(
                    "saksbehandler_epost" to annullerReservasjonDto.saksbehandlerEpost,
                    "reservasjonsnokkel" to annullerReservasjonDto.reservasjonsnøkkel,
                    "now" to LocalDateTime.now(),
                )
            ).asUpdate
        )
    }

    fun hentAktiveReservasjonerForSaksbehandler(epost: String, tx: TransactionalSession): List<ReservasjonV3> {
        return tx.run(
            queryOf(
                """
                   select r.id, r.saksbehandler_epost, r.reservasjonsnokkel, lower(r.gyldig_tidsrom) as fra, upper(r.gyldig_tidsrom) as til 
                   from reservasjon_v3 r
                   where r.saksbehandler_epost = :epost
                   and annullert_for_utlop = false
                   and lower(r.gyldig_tidsrom) < localtimestamp
                   and upper(r.gyldig_tidsrom) > localtimestamp
                """.trimIndent(),
                mapOf(
                    "epost" to epost
                )
            ).map { row ->
                ReservasjonV3(
                    id = row.long("id"),
                    saksbehandlerEpost = row.string("saksbehandler_epost"),
                    reservasjonsnøkkel = row.string("reservasjonsnokkel"),
                    gyldigFra = row.localDateTime("fra"),
                    gyldigTil = row.localDateTime("til"),
                )
            }.asList
        )
    }

    fun hentAktivReservasjonForReservasjonsnøkkel(nøkkel: String, tx: TransactionalSession): ReservasjonV3? {
        return tx.run(
            queryOf(
                """
                   select r.id, r.saksbehandler_epost, r.reservasjonsnokkel, lower(r.gyldig_tidsrom) as fra, upper(r.gyldig_tidsrom) as til 
                   from reservasjon_v3 r
                   where r.reservasjonsnokkel = :nokkel
                   and annullert_for_utlop = false
                   and lower(r.gyldig_tidsrom) < localtimestamp
                   and upper(r.gyldig_tidsrom) > localtimestamp
                """.trimIndent(),
                mapOf(
                    "nokkel" to nøkkel
                )
            ).map { row ->
                ReservasjonV3(
                    id = row.long("id"),
                    saksbehandlerEpost = row.string("saksbehandler_epost"),
                    reservasjonsnøkkel = row.string("reservasjonsnokkel"),
                    gyldigFra = row.localDateTime("fra"),
                    gyldigTil = row.localDateTime("til"),
                )
            }.asSingle
        )
    }
}