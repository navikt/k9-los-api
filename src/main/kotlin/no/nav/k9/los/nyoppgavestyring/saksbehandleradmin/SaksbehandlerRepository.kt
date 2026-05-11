package no.nav.k9.los.nyoppgavestyring.saksbehandleradmin

import kotliquery.*
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import org.apache.commons.text.similarity.LevenshteinDistance
import java.util.Locale
import java.util.Locale.getDefault
import javax.sql.DataSource

class SaksbehandlerRepository(
    private val dataSource: DataSource,
    private val pepClient: IPepClient,
    private val transactionalManager: TransactionalManager
) {
    suspend fun addSaksbehandler(saksbehandler: Saksbehandler): Long {
        val erSkjermet = pepClient.harTilgangTilKode6()
        return using(sessionOf(dataSource)) {
            val saksbehandlerId = it.transaction { tx ->
                val saksbehandlerId = tx.run(
                    queryOf(
                        """
                        insert into saksbehandler as k (navident, navn, epost, enhet, skjermet)
                        values (:navident,:navn,:epost, :enhet, :skjermet)
                        on conflict (epost) do update
                        set navident = :navident,
                            navn = :navn,
                            enhet = :enhet,
                            skjermet = :skjermet
                        returning id
                     """,
                        mapOf(
                            "navident" to saksbehandler.navident,
                            "epost" to saksbehandler.epost.lowercase(getDefault()),
                            "navn" to saksbehandler.navn,
                            "enhet" to saksbehandler.enhet,
                            "skjermet" to erSkjermet
                        )
                    ).map { row -> row.long("id") }.asSingle
                )
                saksbehandlerId!!
            }
            saksbehandlerId
        }
    }

    fun finnSaksbehandlerMedId(id: Long): Saksbehandler? {
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """select * from saksbehandler where id = :id""",
                    mapOf("id" to id)
                ).map { row ->
                    mapSaksbehandler(row)
                }.asSingle
            )
        }!!
    }

    suspend fun finnSaksbehandlerMedEpost(epost: String): Saksbehandler? {
        val skjermet = pepClient.harTilgangTilKode6()

        val saksbehandler = using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx.run(
                    queryOf(
                        "select * from saksbehandler where lower(epost) = lower(:epost) and skjermet = :skjermet",
                        mapOf("epost" to epost, "skjermet" to skjermet)
                    ).map { row ->
                        mapSaksbehandler(row)
                    }.asSingle
                )
            }
        }
        return saksbehandler
    }

    suspend fun finnSaksbehandlerMedIdent(ident: String): Saksbehandler? {
        val skjermet = pepClient.harTilgangTilKode6()

        val saksbehandler = using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        "select * from saksbehandler where lower(navident) = lower(:ident) and skjermet = :skjermet",
                        mapOf("ident" to ident, "skjermet" to skjermet)
                    )
                        .map { row ->
                            mapSaksbehandler(row)
                        }.asSingle
                )
            }

        }

        return saksbehandler
    }

    fun finnSaksbehandlerMedIdentEkskluderKode6(ident: String): Saksbehandler? {
        val saksbehandler = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    "select * from saksbehandler where skjermet = false and lower(navident) = lower(:ident)",
                    mapOf("ident" to ident)
                )
                    .map { row ->
                        mapSaksbehandler(row)
                    }.asSingle
            )
        }
        return saksbehandler
    }

    //Kopi av den andre slettefunksjonen uten gjenbruk, siden den andre skal slettes etterhvert
    fun slettSaksbehandlerForId(tx: TransactionalSession, id: Long, skjermet: Boolean) {
        val saksbehandlerId = tx.run(
            queryOf(
                """
                    select id from saksbehandler where id = :id and skjermet = :skjermet
                """.trimIndent(),
                mapOf("id" to id, "skjermet" to skjermet)
            ).map { row ->
                row.long("id")
            }.asSingle
        )

        if (saksbehandlerId == null) {
            throw IllegalStateException("Fant ikke saksbehandler med id $id")
        }

        //Sletting av reservasjoner ligger her og ikke i reservasjonV3Repository, siden dette ikke er en del av "vanlig"
        //saksgang. Tanken var egentlig at reservasjoner og reservasjon_v3_endring ikke skulle slettes.
        tx.run(
            queryOf(
                """
                    delete from reservasjon_v3_endring where endretav = :saksbehandlerId
                """.trimIndent(),
                mapOf("saksbehandlerId" to saksbehandlerId)
            ).asUpdate
        )

        tx.run(
            queryOf(
                """
                    delete from reservasjon_v3_endring re
                     using reservasjon_v3 r 
                     where r.id = re.annullert_reservasjon_id
                       and r.reservertav = :saksbehandlerId
                """.trimIndent(),
                mapOf("saksbehandlerId" to saksbehandlerId)
            ).asUpdate
        )

        tx.run(
            queryOf(
                """
                    delete from reservasjon_v3_endring re
                     using reservasjon_v3 r 
                     where r.id = re.ny_reservasjon_id
                       and r.reservertav = :saksbehandlerId
                """.trimIndent(),
                mapOf("saksbehandlerId" to saksbehandlerId)
            ).asUpdate
        )

        tx.run(
            queryOf(
                """
                    delete from reservasjon_v3 where reservertav = :saksbehandlerId
                """.trimIndent(),
                mapOf("saksbehandlerId" to saksbehandlerId)
            ).asUpdate
        )


        tx.run(
            queryOf(
                """
                            delete from saksbehandler where id = :saksbehandlerId""",
                mapOf("saksbehandlerId" to saksbehandlerId)
            ).asUpdate
        )
    }

    fun slettSaksbehandler(tx: TransactionalSession, epost: String, skjermet: Boolean) {
        val saksbehandlerId = tx.run(
            queryOf(
                """
                    select id from saksbehandler where lower(epost) = lower(:epost) and skjermet = :skjermet
                """.trimIndent(),
                mapOf("epost" to epost.lowercase(Locale.getDefault()), "skjermet" to skjermet)
            ).map { row ->
                row.long("id")
            }.asSingle
        )

        if (saksbehandlerId == null) {
            throw IllegalStateException("Fant ikke saksbehandler med epost $epost")
        }

        //Sletting av reservasjoner ligger her og ikke i reservasjonV3Repository, siden dette ikke er en del av "vanlig"
        //saksgang. Tanken var egentlig at reservasjoner og reservasjon_v3_endring ikke skulle slettes.
        tx.run(
            queryOf(
                """
                    delete from reservasjon_v3_endring where endretav = :saksbehandlerId
                """.trimIndent(),
                mapOf("saksbehandlerId" to saksbehandlerId)
            ).asUpdate
        )

        tx.run(
            queryOf(
                """
                    delete from reservasjon_v3_endring re
                     using reservasjon_v3 r 
                     where r.id = re.annullert_reservasjon_id
                       and r.reservertav = :saksbehandlerId
                """.trimIndent(),
                mapOf("saksbehandlerId" to saksbehandlerId)
            ).asUpdate
        )

        tx.run(
            queryOf(
                """
                    delete from reservasjon_v3_endring re
                     using reservasjon_v3 r 
                     where r.id = re.ny_reservasjon_id
                       and r.reservertav = :saksbehandlerId
                """.trimIndent(),
                mapOf("saksbehandlerId" to saksbehandlerId)
            ).asUpdate
        )


        tx.run(
            queryOf(
                """
                    delete from reservasjon_v3 where reservertav = :saksbehandlerId
                """.trimIndent(),
                mapOf("saksbehandlerId" to saksbehandlerId)
            ).asUpdate
        )

        tx.run(
            queryOf(
                """
                            delete from saksbehandler where id = :saksbehandlerId""",
                mapOf("saksbehandlerId" to saksbehandlerId)
            ).asUpdate
        )
    }

    suspend fun hentAlleSaksbehandlere(): List<Saksbehandler> {
        return transactionalManager.transactionSuspend { tx ->
            hentAlleSaksbehandlere(tx)
        }
    }

    suspend fun hentAlleSaksbehandlere(tx: TransactionalSession): List<Saksbehandler> {
        val skjermet = pepClient.harTilgangTilKode6()
        val identer = using(sessionOf(dataSource)) {
            tx.run(
                queryOf(
                    "select * from saksbehandler where skjermet = :skjermet",
                    mapOf("skjermet" to skjermet)
                )
                    .map { row ->
                        mapSaksbehandler(row)
                    }.asList
            )
        }
        return identer
    }

    suspend fun sokSaksbehandler(søkestreng: String): Saksbehandler {
        val alleSaksbehandlere = hentAlleSaksbehandlere()

        fun levenshtein(lhs: CharSequence, rhs: CharSequence): Double {
            return LevenshteinDistance().apply(lhs, rhs).toDouble()
        }

        var d = Double.MAX_VALUE
        var i = -1
        for ((index, saksbehandler) in alleSaksbehandlere.withIndex()) {
            if (saksbehandler.navident == null) {
                continue
            }
            if (saksbehandler.navn != null && saksbehandler.navn!!.lowercase(Locale.getDefault())
                    .contains(søkestreng, true)
            ) {
                i = index
                break
            }

            var distance = levenshtein(
                søkestreng.lowercase(Locale.getDefault()),
                saksbehandler.navident!!.lowercase(Locale.getDefault())
            )
            if (distance < d) {
                d = distance
                i = index
            }
            distance = levenshtein(
                søkestreng.lowercase(Locale.getDefault()),
                saksbehandler.navn?.lowercase(Locale.getDefault()) ?: ""
            )
            if (distance < d) {
                d = distance
                i = index
            }
            distance = levenshtein(
                søkestreng.lowercase(Locale.getDefault()),
                saksbehandler.epost.lowercase(Locale.getDefault())
            )
            if (distance < d) {
                d = distance
                i = index
            }
        }
        return alleSaksbehandlere[i]
    }

    private fun mapSaksbehandler(row: Row): Saksbehandler {
        return Saksbehandler(
            id = row.long("id"),
            navident = row.stringOrNull("navident"),
            navn = row.stringOrNull("navn"),
            epost = row.string("epost").lowercase(Locale.getDefault()),
            enhet = row.stringOrNull("enhet")
        )
    }
}
