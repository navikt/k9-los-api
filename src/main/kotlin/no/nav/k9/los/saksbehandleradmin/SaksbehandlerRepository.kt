package no.nav.k9.los.saksbehandleradmin

import kotliquery.*
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.innloggetBruker
import kotlin.coroutines.coroutineContext
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRepository
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import org.apache.commons.text.similarity.LevenshteinDistance
import java.util.Locale
import java.util.Locale.getDefault
import javax.sql.DataSource

class SaksbehandlerRepository(
    private val dataSource: DataSource,
    private val pepClient: IPepClient,
    private val transactionalManager: TransactionalManager,
    private val områdeRepository: OmrådeRepository
) {
    /**
     * Upserter en saksbehandler basert på epost og kobler den til [område].
     *
     * Kun epost og områdekoblingen berøres. De øvrige feltene på saksbehandleren
     * (navident, navn, enhet, skjermet) røres ikke her – de vedlikeholdes av
     * [vedlikeholdSaksbehandler] når saksbehandleren selv logger inn.
     */
    fun addSaksbehandler(epost: String, område: Områder): Long {
        return using(sessionOf(dataSource)) {
            it.transaction { tx ->
                val saksbehandlerId = tx.run(
                    queryOf(
                        """
                        insert into saksbehandler (epost)
                        values (:epost)
                        on conflict (epost) do update
                        set epost = excluded.epost
                        returning id
                     """,
                        mapOf(
                            "epost" to epost.lowercase(getDefault()),
                        )
                    ).map { row -> row.long("id") }.asSingle
                )!!

                val omradeId = områdeRepository.hentOmråde(område, tx).id
                tx.run(
                    queryOf(
                        """
                        insert into saksbehandler_omrade (saksbehandler_id, omrade_id)
                        values (:saksbehandlerId, :omradeId)
                        on conflict do nothing
                        """.trimIndent(),
                        mapOf(
                            "saksbehandlerId" to saksbehandlerId,
                            "omradeId" to omradeId,
                        )
                    ).asUpdate
                )

                saksbehandlerId
            }
        }
    }

    /**
     * Vedlikeholder de saksbehandler-eide feltene (navident, navn, enhet, skjermet) for en
     * eksisterende saksbehandler, matchet på epost. Områdekoblinger røres ikke her – de
     * håndteres av [addSaksbehandler].
     *
     * Bruker en ren UPDATE (ikke upsert). Saksbehandleren må allerede finnes – raden legges inn
     * av [addSaksbehandler] når avdelingsleder registrerer eposten. En upsert her ville dessuten
     * forbrukt en sekvensverdi på id-kolonnen ved konflikt, og forskjøvet genererte id-er.
     */
    suspend fun vedlikeholdSaksbehandler(saksbehandler: Saksbehandler): Long {
        val erSkjermet = pepClient.erKode6Bruker(coroutineContext.innloggetBruker())

        return using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        """
                        update saksbehandler
                        set navident = coalesce(:navident, navident),
                            navn = coalesce(:navn, navn),
                            enhet = coalesce(:enhet, enhet),
                            skjermet = :skjermet
                        where lower(epost) = lower(:epost)
                        returning id
                     """,
                        mapOf(
                            "navident" to saksbehandler.navident,
                            "epost" to saksbehandler.epost.lowercase(getDefault()),
                            "navn" to saksbehandler.navn,
                            "enhet" to saksbehandler.enhet,
                            "skjermet" to erSkjermet,
                        )
                    ).map { row -> row.long("id") }.asSingle
                ) ?: throw IllegalStateException("Fant ikke saksbehandler med epost ${saksbehandler.epost} for vedlikehold")
            }
        }
    }

    fun finnSaksbehandlerMedId(id: Long): Saksbehandler? {
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """$SAKSBEHANDLER_SELECT where s.id = :id""",
                    mapOf("id" to id)
                ).map { row ->
                    mapSaksbehandler(row)
                }.asSingle
            )
        }!!
    }

    suspend fun finnSaksbehandlerMedEpost(epost: String): Saksbehandler? {
        val skjermet = pepClient.erKode6Bruker(coroutineContext.innloggetBruker())

        val saksbehandler = using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx.run(
                    queryOf(
                        "$SAKSBEHANDLER_SELECT where lower(s.epost) = lower(:epost) and s.skjermet = :skjermet",
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
        val skjermet = pepClient.erKode6Bruker(coroutineContext.innloggetBruker())

        val saksbehandler = using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        "$SAKSBEHANDLER_SELECT where lower(s.navident) = lower(:ident) and s.skjermet = :skjermet",
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
                    "$SAKSBEHANDLER_SELECT where s.skjermet = false and lower(s.navident) = lower(:ident)",
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

    fun fjernOmrådeFraSaksbehandler(tx: TransactionalSession, epost: String, skjermet: Boolean, område: Områder) {
        val antallSlettet = tx.run(
            queryOf(
                """
                    delete from saksbehandler_omrade so
                    using saksbehandler s, omrade o
                    where so.saksbehandler_id = s.id
                      and so.omrade_id = o.id
                      and lower(s.epost) = lower(:epost)
                      and s.skjermet = :skjermet
                      and o.ekstern_id = :omradeEksternId
                """.trimIndent(),
                mapOf(
                    "epost" to epost.lowercase(Locale.getDefault()),
                    "skjermet" to skjermet,
                    "omradeEksternId" to område.eksternId
                )
            ).asUpdate
        )

        if (antallSlettet == 0) {
            throw IllegalStateException("Fant ikke område ${område.eksternId} for saksbehandler med epost $epost")
        }
    }

    suspend fun hentAlleSaksbehandlere(): List<Saksbehandler> {
        return transactionalManager.transactionSuspend { tx ->
            hentAlleSaksbehandlere(tx)
        }
    }

    suspend fun hentAlleSaksbehandlere(tx: TransactionalSession): List<Saksbehandler> {
        val skjermet = pepClient.erKode6Bruker(coroutineContext.innloggetBruker())
        val identer = using(sessionOf(dataSource)) {
            tx.run(
                queryOf(
                    "$SAKSBEHANDLER_SELECT where s.skjermet = :skjermet",
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
        val områder = row.stringOrNull("omrade_ekstern_ider")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.map { Områder.fraEksternId(it) }
            ?: emptyList()

        if (områder.isEmpty()) {
            throw IllegalStateException("Saksbehandler ${row.long("id")} mangler områdekobling")
        }

        return Saksbehandler(
            id = row.long("id"),
            navident = row.stringOrNull("navident"),
            navn = row.stringOrNull("navn"),
            epost = row.string("epost").lowercase(Locale.getDefault()),
            enhet = row.stringOrNull("enhet"),
            områder = områder
        )
    }

    companion object {
        private const val SAKSBEHANDLER_SELECT =
            """
            select *
            from (select s.id,
                         s.navident,
                         s.navn,
                         s.epost,
                         s.enhet,
                         s.skjermet,
                         string_agg(distinct o.ekstern_id, ',') as omrade_ekstern_ider
                  from saksbehandler s
                           left join saksbehandler_omrade so on so.saksbehandler_id = s.id
                           left join omrade o on o.id = so.omrade_id
                  group by s.id, s.navident, s.navn, s.epost, s.enhet, s.skjermet) s
            """
    }
}
