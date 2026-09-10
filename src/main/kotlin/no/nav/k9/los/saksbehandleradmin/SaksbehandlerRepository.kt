package no.nav.k9.los.saksbehandleradmin

import kotliquery.*
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRepository
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.uttrekk.UttrekkStatus
import org.apache.commons.text.similarity.LevenshteinDistance
import java.util.*
import java.util.Locale.getDefault
import javax.sql.DataSource

class SaksbehandlerRepository(
    private val dataSource: DataSource,
    private val transactionalManager: TransactionalManager,
    private val områdeRepository: OmrådeRepository
) {
    fun opprettSaksbehandler(epost: String, område: Områder, skjermet: Boolean = false): Long {
        return using(sessionOf(dataSource)) {
            it.transaction { tx ->
                val saksbehandlerId = tx.run(
                    queryOf(
                        """
                        insert into saksbehandler (epost, skjermet)
                        values (:epost, :skjermet)
                        returning id
                     """,
                        mapOf(
                            "epost" to epost.lowercase(getDefault()),
                            "skjermet" to skjermet,
                        )
                    ).map { row -> row.long("id") }.asSingle
                )!!
                leggTilOmråde(tx, saksbehandlerId, område)
                saksbehandlerId
            }
        }
    }

    fun vedlikeholdSaksbehandler(
        saksbehandler: Saksbehandler,
    ): Long {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """
                    update saksbehandler
                    set navident = :navident,
                        navn = :navn,
                        epost = :epost,
                        enhet = :enhet,
                        skjermet = :skjermet,
                        sist_oppdatert = :sistoppdatert
                    where id = :id
                    returning id
                    """.trimIndent(),
                    mapOf(
                        "id" to saksbehandler.id,
                        "navident" to saksbehandler.navident,
                        "navn" to saksbehandler.navn,
                        "epost" to saksbehandler.epost.lowercase(getDefault()),
                        "enhet" to saksbehandler.enhet,
                        "skjermet" to saksbehandler.skjermet,
                        "sistoppdatert" to saksbehandler.sistOppdatert
                    )
                ).map { row -> row.long("id") }.asSingle
            ) ?: throw IllegalStateException("Fant ikke saksbehandler med id ${saksbehandler.id} for vedlikehold")
        }
    }

    fun leggTilOmråde(saksbehandlerId: Long, område: Områder) {
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx -> leggTilOmråde(tx, saksbehandlerId, område) }
        }
    }

    private fun leggTilOmråde(tx: TransactionalSession, saksbehandlerId: Long, område: Områder) {
        val områdeId = områdeRepository.hentOmråde(område, tx).id
        tx.run(
            queryOf(
                """
                insert into saksbehandler_omrade (saksbehandler_id, omrade_id)
                values (:saksbehandlerId, :omradeId)
                on conflict do nothing
                """.trimIndent(),
                mapOf("saksbehandlerId" to saksbehandlerId, "omradeId" to områdeId)
            ).asUpdate
        )
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
        }
    }

    fun finnSaksbehandlerMedEpost(epost: String, skjermet: Boolean): Saksbehandler? {
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

    fun finnSaksbehandlerMedEpost(epost: String): Saksbehandler? = using(sessionOf(dataSource)) { session ->
        session.run(
            queryOf(
                "$SAKSBEHANDLER_SELECT where lower(s.epost) = lower(:epost)",
                mapOf("epost" to epost),
            ).map { row -> mapSaksbehandler(row) }.asSingle
        )
    }

    fun finnSaksbehandlerMedIdent(ident: String, skjermet: Boolean): Saksbehandler? {
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

    fun finnSaksbehandlerMedIdent(ident: String): Saksbehandler? = using(sessionOf(dataSource)) { session ->
        session.run(
            queryOf(
                "$SAKSBEHANDLER_SELECT where lower(s.navident) = lower(:ident)",
                mapOf("ident" to ident),
            ).map { row -> mapSaksbehandler(row) }.asSingle
        )
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

    fun hentForSletting(tx: TransactionalSession, id: Long): Saksbehandler? {
        val finnes = tx.run(queryOf(
            "select id from saksbehandler where id = :id for update", mapOf("id" to id)
        ).map { it.long("id") }.asSingle) ?: return null
        // FK-låsene hindrer nye områdekoblinger mens vi avgjør om dette er siste område.
        tx.run(queryOf(
            "select omrade_id from saksbehandler_omrade where saksbehandler_id = :id for update",
            mapOf("id" to finnes)
        ).map { it.long("omrade_id") }.asList)
        return tx.run(queryOf(
            "$SAKSBEHANDLER_SELECT where s.id = :id", mapOf("id" to finnes)
        ).map { mapSaksbehandler(it) }.asSingle)
    }

    fun slettFraOmråde(tx: TransactionalSession, saksbehandler: Saksbehandler, område: Områder) {
        require(område in saksbehandler.områder)
        val sisteOmråde = saksbehandler.områder.size == 1
        val parametre = mapOf(
            "id" to saksbehandler.id,
            "omradeId" to områdeRepository.hentOmråde(område, tx).id,
            "sisteOmrade" to sisteOmråde,
            "k9" to (område == Områder.K9),
            "kjorer" to UttrekkStatus.KJØRER.name,
        )
        tx.run(queryOf(
            "select id from lagret_sok where laget_av = :id and omrade_id = :omradeId for update", parametre
        ).map { it.long("id") }.asList)

        // Uttrekk må slettes før kildesøk, ellers mister vi områdets proveniens (ON DELETE SET NULL).
        // Uttrekk uten kildesøk er legacy K9-data og ryddes bare når siste K9-kobling slettes.
        val uttrekkSomSkalSlettes = """
            select u.id from uttrekk u
            left join lagret_sok ls on ls.id = u.lagret_sok_id
            where u.laget_av = :id and :k9
              and (ls.omrade_id = :omradeId or (:sisteOmrade and u.lagret_sok_id is null))
        """.trimIndent()
        val statuser = tx.run(queryOf(
            "select status from uttrekk where id in ($uttrekkSomSkalSlettes) for update", parametre
        ).map { it.string("status") }.asList)
        check(UttrekkStatus.KJØRER.name !in statuser) { "Kan ikke slette saksbehandler med kjørende uttrekk" }
        val andreUttrekkBerøres = tx.run(queryOf(
            """
            select exists (
                select 1 from uttrekk u join lagret_sok ls on ls.id = u.lagret_sok_id
                where ls.laget_av = :id and ls.omrade_id = :omradeId
                  and u.id not in ($uttrekkSomSkalSlettes)
            ) as finnes
            """.trimIndent(), parametre
        ).map { it.boolean("finnes") }.asSingle)!!
        check(!andreUttrekkBerøres) { "Kan ikke slette søk med uttrekk som ikke tilhører valgt område og saksbehandler" }
        tx.run(queryOf("delete from uttrekk where id in ($uttrekkSomSkalSlettes) and status <> :kjorer", parametre).asUpdate)
        tx.run(queryOf(
            "delete from lagret_sok where laget_av = :id and omrade_id = :omradeId", parametre
        ).asUpdate)

        // Bevar andre områders endringshistorikk, også når samme bruker har utført endringen.
        tx.run(queryOf(
            "select id from reservasjon_v3 where reservertav = :id and omrade_id = :omradeId for update", parametre
        ).map { it.long("id") }.asList)
        tx.run(queryOf(
            """
            delete from reservasjon_v3_endring re
            where exists (
                select 1 from reservasjon_v3 r
                where r.id in (re.annullert_reservasjon_id, re.ny_reservasjon_id)
                  and r.omrade_id = :omradeId and (r.reservertav = :id or re.endretav = :id)
            ) and not exists (
                select 1 from reservasjon_v3 r
                where r.id in (re.annullert_reservasjon_id, re.ny_reservasjon_id) and r.omrade_id <> :omradeId
            )
            """.trimIndent(), parametre
        ).asUpdate)
        tx.run(queryOf(
            "delete from reservasjon_v3 where reservertav = :id and omrade_id = :omradeId", parametre
        ).asUpdate)
        if (sisteOmråde) {
            // Eventuelle gjenværende FK-er til andre områders data skal stoppe og rulle tilbake hele slettingen.
            tx.run(queryOf("delete from saksbehandler where id = :id", parametre).asUpdate)
        } else {
            tx.run(queryOf(
                "delete from saksbehandler_omrade where saksbehandler_id = :id and omrade_id = :omradeId", parametre
            ).asUpdate)
        }
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

    fun hentAlleSaksbehandlere(område: Områder, skjermet: Boolean): List<Saksbehandler> {
        return transactionalManager.transaction { tx ->
            hentAlleSaksbehandlere(tx, område, skjermet)
        }
    }

    fun hentAlleSaksbehandlere(tx: TransactionalSession, område: Områder, skjermet: Boolean): List<Saksbehandler> {
        val identer = using(sessionOf(dataSource)) {
            tx.run(
                queryOf(
                    """
                    $SAKSBEHANDLER_SELECT
                    where s.skjermet = :skjermet
                      and exists (select 1 from saksbehandler_omrade so2
                                  join omrade o2 on o2.id = so2.omrade_id
                                  where so2.saksbehandler_id = s.id and o2.ekstern_id = :omradeEksternId)
                    """.trimIndent(),
                    mapOf(
                        "skjermet" to skjermet,
                        "omradeEksternId" to område.eksternId
                    )
                )
                    .map { row ->
                        mapSaksbehandler(row)
                    }.asList
            )
        }
        return identer
    }

    fun sokSaksbehandler(søkestreng: String, område: Områder, skjermet: Boolean): Saksbehandler {
        val alleSaksbehandlere = hentAlleSaksbehandlere(område, skjermet)

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
            områder = områder,
            skjermet = row.boolean("skjermet"),
            sistOppdatert = row.localDateTimeOrNull("sist_oppdatert"),
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
                         s.sist_oppdatert,
                         string_agg(distinct o.ekstern_id, ',') as omrade_ekstern_ider
                  from saksbehandler s
                           left join saksbehandler_omrade so on so.saksbehandler_id = s.id
                           left join omrade o on o.id = so.omrade_id
                  group by s.id, s.navident, s.navn, s.epost, s.enhet, s.skjermet, s.sist_oppdatert) s
            """
    }
}
