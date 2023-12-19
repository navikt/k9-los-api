package no.nav.k9.los.nyoppgavestyring.reservasjon

import kotliquery.TransactionalSession
import kotliquery.queryOf
import org.postgresql.util.PSQLException
import java.time.LocalDateTime

class ReservasjonV3Repository(
) {
    fun lagreReservasjon(reservasjonV3: ReservasjonV3, tx: TransactionalSession): ReservasjonV3 {
        try {
            return reservasjonV3.copy(
                tx.updateAndReturnGeneratedKey(
                    queryOf(
                        """
                    insert into RESERVASJON_V3(reservertAv, reservasjonsnokkel, gyldig_tidsrom, kommentar)
                    values (:reservertAv, :nokkel, tsrange(:gyldig_fra, :gyldig_til), :kommentar)
                """.trimIndent(),
                        mapOf(
                            "reservertAv" to reservasjonV3.reservertAv,
                            "nokkel" to reservasjonV3.reservasjonsnøkkel,
                            "kommentar" to reservasjonV3.kommentar,
                            "gyldig_fra" to reservasjonV3.gyldigFra,
                            "gyldig_til" to reservasjonV3.gyldigTil
                        )
                    )
                )!!
            )
        } catch (e: PSQLException) {
            if (e.sqlState == "23P01") {//exclusion_violation
                throw AlleredeReservertException("${reservasjonV3.reservasjonsnøkkel} er allerede reservert!")
            } else if (e.sqlState == "23503" && e.message!!.contains("fk_reservasjon_v3_01")) {
                throw IllegalArgumentException("Saksbehandler med id ${reservasjonV3.reservertAv} finnes ikke")
            } else {
                throw e
            }
        }
    }

    fun endreReservasjon(reservasjonSomSkalEndres: ReservasjonV3, endretAvBrukerId: Long, nySaksbehandlerId: Long?, nyTildato: LocalDateTime?, kommentar: String?, tx: TransactionalSession) : ReservasjonV3 {
        val annullertReservasjonId = annullerAktivReservasjon(reservasjonSomSkalEndres, kommentar ?: "", tx)
        val nyReservasjon = lagreReservasjon(
            ReservasjonV3(
                reservasjonsnøkkel = reservasjonSomSkalEndres.reservasjonsnøkkel,
                reservertAv = nySaksbehandlerId ?: reservasjonSomSkalEndres.reservertAv,
                kommentar = kommentar ?: reservasjonSomSkalEndres.kommentar,
                gyldigFra = reservasjonSomSkalEndres.gyldigFra,
                gyldigTil = nyTildato ?: reservasjonSomSkalEndres.gyldigTil,
            ),
            tx
        )

        lagreEndring(
            ReservasjonV3Endring(
                annullertReservasjonId = annullertReservasjonId,
                nyReservasjonId = nyReservasjon.id,
                endretAv = endretAvBrukerId
            ),
            tx
        )

        return nyReservasjon
    }

    fun annullerAktivReservasjonOgLagreEndring(
        aktivReservasjon: ReservasjonV3,
        kommentar: String,
        innloggetBrukerId: Long,
        tx: TransactionalSession
    ) {
        val annullertReservasjonId = annullerAktivReservasjon(aktivReservasjon, kommentar, tx)
        lagreEndring(
            ReservasjonV3Endring(
                annullertReservasjonId = annullertReservasjonId,
                nyReservasjonId = null,
                endretAv = innloggetBrukerId
            ),
            tx
        )
    }

    fun forlengReservasjon(
        aktivReservasjon: ReservasjonV3,
        endretAvBrukerId: Long,
        nyTildato: LocalDateTime,
        kommentar: String,
        tx: TransactionalSession
    ): ReservasjonV3 {
        val annullertReservasjonId = annullerAktivReservasjon(aktivReservasjon, kommentar, tx)
        val nyReservasjon = lagreReservasjon(
            ReservasjonV3(
                reservertAv = aktivReservasjon.reservertAv,
                reservasjonsnøkkel = aktivReservasjon.reservasjonsnøkkel,
                kommentar = kommentar,
                gyldigFra = aktivReservasjon.gyldigFra,
                gyldigTil = nyTildato,
            ),
            tx
        )

        lagreEndring(
            ReservasjonV3Endring(
                annullertReservasjonId = annullertReservasjonId,
                nyReservasjonId = nyReservasjon.id,
                endretAv = endretAvBrukerId
            ),
            tx
        )
        return nyReservasjon
    }

    fun overførReservasjon(
        aktivReservasjon: ReservasjonV3,
        saksbehandlerSomSkalHaReservasjonId: Long,
        endretAvBrukerId: Long,
        kommentar: String,
        reserverTil: LocalDateTime,
        tx: TransactionalSession
    ) : ReservasjonV3 {
        val overføringstidspunkt = LocalDateTime.now()

        val annullertReservasjonId = annullerAktivReservasjon(aktivReservasjon, kommentar, tx)

        val nyReservasjon = lagreReservasjon(
            ReservasjonV3(
                reservertAv = saksbehandlerSomSkalHaReservasjonId,
                reservasjonsnøkkel = aktivReservasjon.reservasjonsnøkkel,
                kommentar = kommentar,
                gyldigFra = overføringstidspunkt,
                gyldigTil = reserverTil
            ),
            tx
        )

        lagreEndring(
            ReservasjonV3Endring(
                annullertReservasjonId = annullertReservasjonId,
                nyReservasjonId = nyReservasjon.id,
                endretAv = endretAvBrukerId,
            ), tx
        )
        return nyReservasjon
    }

    private fun annullerAktivReservasjon(aktivReservasjon: ReservasjonV3, kommentar: String, tx: TransactionalSession): Long {
        return tx.updateAndReturnGeneratedKey( //TODO: reservasjon allerede utløpt, men ikke annullert? Returnere Long?
            queryOf(
                """
                    UPDATE public.reservasjon_v3
                    SET annullert_for_utlop = true, sist_endret = :now, kommentar = :kommentar
                    WHERE reservertAv = :reservertAv
                    and reservasjonsnokkel = :reservasjonsnokkel
                    and upper(gyldig_tidsrom) > localtimestamp
                    and annullert_for_utlop = false
                    """.trimIndent(),
                mapOf(
                    "reservertAv" to aktivReservasjon.reservertAv,
                    "reservasjonsnokkel" to aktivReservasjon.reservasjonsnøkkel,
                    "kommentar" to kommentar,
                    "now" to LocalDateTime.now(),
                )
            )
        )!!
    }

    fun hentAktiveReservasjonerForSaksbehandler(
        saksbehandlerId: Long,
        tx: TransactionalSession
    ): List<ReservasjonV3> {
        return tx.run(
            queryOf(
                """
                   select r.id, r.reservertAv, r.reservasjonsnokkel, lower(r.gyldig_tidsrom) as fra, upper(r.gyldig_tidsrom) as til, r.annullert_for_utlop, kommentar as kommentar 
                   from reservasjon_v3 r
                   where r.reservertAv = :reservertAv
                   and annullert_for_utlop = false
                   and lower(r.gyldig_tidsrom) < localtimestamp
                   and upper(r.gyldig_tidsrom) > localtimestamp
                """.trimIndent(),
                mapOf(
                    "reservertAv" to saksbehandlerId
                )
            ).map { row ->
                ReservasjonV3(
                    id = row.long("id"),
                    reservertAv = row.long("reservertAv"),
                    reservasjonsnøkkel = row.string("reservasjonsnokkel"),
                    kommentar = row.string("kommentar"),
                    gyldigFra = row.localDateTime("fra"),
                    gyldigTil = row.localDateTime("til"),
                )
            }.asList
        )
    }

    fun hentAlleAktiveReservasjoner(
        tx: TransactionalSession
    ): List<ReservasjonV3> {
        return tx.run(
            queryOf(
                """
                   select r.id, r.reservertAv, r.reservasjonsnokkel, lower(r.gyldig_tidsrom) as fra, upper(r.gyldig_tidsrom) as til, r.annullert_for_utlop, kommentar as kommentar 
                   from reservasjon_v3 r
                   where annullert_for_utlop = false
                   and lower(r.gyldig_tidsrom) < localtimestamp
                   and upper(r.gyldig_tidsrom) > localtimestamp
                """.trimIndent()
            ).map { row ->
                ReservasjonV3(
                    id = row.long("id"),
                    reservertAv = row.long("reservertAv"),
                    reservasjonsnøkkel = row.string("reservasjonsnokkel"),
                    kommentar = row.string("kommentar"),
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
                   select r.id, r.reservertAv, r.reservasjonsnokkel, lower(r.gyldig_tidsrom) as fra, upper(r.gyldig_tidsrom) as til, r.annullert_for_utlop , kommentar as kommentar
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
                    kommentar = row.string("kommentar"),
                    annullertFørUtløp = row.boolean("annullert_for_utlop"),
                    gyldigFra = row.localDateTime("fra"),
                    gyldigTil = row.localDateTime("til"),
                )
            }.asSingle
        )
    }

    private fun lagreEndring(endring: ReservasjonV3Endring, tx: TransactionalSession) {
        tx.run(
            queryOf(
                """
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