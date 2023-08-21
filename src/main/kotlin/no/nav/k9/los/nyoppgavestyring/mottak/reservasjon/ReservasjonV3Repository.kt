package no.nav.k9.los.nyoppgavestyring.mottak.reservasjon

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.domene.modell.Saksbehandler
import org.postgresql.util.PSQLException
import java.time.LocalDateTime

class ReservasjonV3Repository(
) {
    fun lagreReservasjon(reservasjonV3: ReservasjonV3, tx: TransactionalSession): Long {
        try {
            return tx.updateAndReturnGeneratedKey(
                queryOf(
                    """
                    insert into RESERVASJON_V3(reservertAv, reservasjonsnokkel, gyldig_tidsrom)
                    values (:reservertAv, :nokkel, tsrange(:gyldig_fra, :gyldig_til))
                """.trimIndent(),
                    mapOf(
                        "reservertAv" to reservasjonV3.reservertAv,
                        "nokkel" to reservasjonV3.reservasjonsnøkkel,
                        "gyldig_fra" to reservasjonV3.gyldigFra,
                        "gyldig_til" to reservasjonV3.gyldigTil
                    )
                )
            )!!
        } catch (e: PSQLException) {
            if (e.sqlState == "23P01") {//exclusion_violation
                throw IllegalArgumentException("${reservasjonV3.reservasjonsnøkkel} er allerede reservert!")
            } else {
                throw e
            }
        }
    }

    fun annullerAktivReservasjonOgLagreEndring(saksbehandler: Saksbehandler, innloggetBruker: Saksbehandler, reservasjonsnøkkel: String, tx: TransactionalSession) {
        val annullertReservasjonId = annullerAktivReservasjon(saksbehandler, reservasjonsnøkkel, tx)
        lagreEndring(
            ReservasjonV3Endring(
                annullertReservasjonId = annullertReservasjonId,
                nyReservasjonId = null,
                endretAv = innloggetBruker.id!!
            ),
            tx
        )
    }

    fun forlengReservasjon(aktivReservasjon: ReservasjonV3, saksbehandlerSomHarReservasjon: Saksbehandler, innloggetBruker: Saksbehandler, nyTildato: LocalDateTime, tx: TransactionalSession) {
        val annullertReservasjonId = annullerAktivReservasjon(saksbehandlerSomHarReservasjon, aktivReservasjon.reservasjonsnøkkel, tx)
        val nyReservasjonId = lagreReservasjon(
            ReservasjonV3(
                reservertAv = saksbehandlerSomHarReservasjon.id!!,
                reservasjonsnøkkel = aktivReservasjon.reservasjonsnøkkel,
                gyldigFra = aktivReservasjon.gyldigFra,
                gyldigTil = nyTildato,
            ),
            tx
        )

        lagreEndring(
            ReservasjonV3Endring(
                annullertReservasjonId = annullertReservasjonId,
                nyReservasjonId = nyReservasjonId,
                endretAv = innloggetBruker.id!!
            ),
            tx
        )
    }

    fun overførReservasjon(
            saksbehandlerSomHarReservasjon: Saksbehandler,
            saksbehandlerSomSkalHaReservasjon: Saksbehandler,
            innloggetBruker: Saksbehandler,
            reserverTil: LocalDateTime,
            reservasjonsnøkkel: String,
            tx: TransactionalSession) {
        val overføringstidspunkt = LocalDateTime.now()

        val annullertReservasjonId = annullerAktivReservasjon(saksbehandlerSomHarReservasjon, reservasjonsnøkkel, tx)

        val nyReservasjonId = lagreReservasjon(
            ReservasjonV3(
                reservertAv = saksbehandlerSomSkalHaReservasjon.id!!,
                reservasjonsnøkkel = reservasjonsnøkkel,
                gyldigFra = overføringstidspunkt,
                gyldigTil = reserverTil
            ),
            tx
        )

        lagreEndring(
            ReservasjonV3Endring(
                annullertReservasjonId = annullertReservasjonId,
                nyReservasjonId = nyReservasjonId,
                endretAv = innloggetBruker.id!!,
            ), tx
        )
    }

    private fun annullerAktivReservasjon(saksbehandler: Saksbehandler, reservasjonsnøkkel: String, tx: TransactionalSession) : Long {
        return tx.updateAndReturnGeneratedKey(
            queryOf(
                """
                    UPDATE public.reservasjon_v3
                    SET annullert_for_utlop = true, sist_endret = :now
                    WHERE reservertAv = :reservertAv
                    and reservasjonsnokkel = :reservasjonsnokkel
                    and upper(gyldig_tidsrom) > localtimestamp
                    and annullert_for_utlop = false
                    """.trimIndent(),
                mapOf(
                    "reservertAv" to saksbehandler.id,
                    "reservasjonsnokkel" to reservasjonsnøkkel,
                    "now" to LocalDateTime.now(),
                )
            )
        )!!
    }

    fun hentAktiveReservasjonerForSaksbehandler(saksbehandler: Saksbehandler, tx: TransactionalSession): List<ReservasjonV3> {
        return tx.run(
            queryOf(
                """
                   select r.id, r.reservertAv, r.reservasjonsnokkel, lower(r.gyldig_tidsrom) as fra, upper(r.gyldig_tidsrom) as til, r.annullert_for_utlop 
                   from reservasjon_v3 r
                   where r.reservertAv = :reservertAv
                   and annullert_for_utlop = false
                   and lower(r.gyldig_tidsrom) < localtimestamp
                   and upper(r.gyldig_tidsrom) > localtimestamp
                """.trimIndent(),
                mapOf(
                    "reservertAv" to saksbehandler.id
                )
            ).map { row ->
                ReservasjonV3(
                    id = row.long("id"),
                    reservertAv = row.long("reservertAv"),
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
                   select r.id, r.reservertAv, r.reservasjonsnokkel, lower(r.gyldig_tidsrom) as fra, upper(r.gyldig_tidsrom) as til, r.annullert_for_utlop 
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
                    reservertAv = row.long("reservertAv"),
                    reservasjonsnøkkel = row.string("reservasjonsnokkel"),
                    annullertFørUtløp = row.boolean("annullert_for_utlop"),
                    gyldigFra = row.localDateTime("fra"),
                    gyldigTil = row.localDateTime("til"),
                )
            }.asSingle
        )
    }

    private fun lagreEndring(endring: ReservasjonV3Endring, tx: TransactionalSession) {
        tx.run(
            queryOf("""
                insert into RESERVASJON_V3_ENDRING(annullert_reservasjon_id, ny_reservasjon_id, endretAv)
                values(:annullert_reservasjon_id, :ny_reservasjon_id, :endretAv)
            """.trimIndent(),
                mapOf(
                    "annullert_reservasjon_id" to endring.annullertReservasjonId,
                    "ny_reservasjon_id" to endring.nyReservasjonId,
                    "endretAv" to endring.endretAv
                )
            ).asUpdate
        )
    }
}