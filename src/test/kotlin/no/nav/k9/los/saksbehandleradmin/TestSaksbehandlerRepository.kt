package no.nav.k9.los.saksbehandleradmin

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRepository
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import java.util.Locale.getDefault
import javax.sql.DataSource

class TestSaksbehandlerRepository(
    val dataSource: DataSource,
    områdeRepository: OmrådeRepository,
    val kode6: Boolean = false,
) {
    private val saksbehandlerRepository =
        SaksbehandlerRepository(dataSource, TransactionalManager(dataSource), områdeRepository)

    fun opprettSaksbehandler(opprettSaksbehandler: OpprettSaksbehandler): Saksbehandler {
        return using(sessionOf(dataSource)) {
            val saksbehandlerId = it.transaction { tx ->
                val saksbehandlerId = tx.run(
                    queryOf(
                        """
                        insert into saksbehandler as k (navident, navn, epost, enhet, skjermet)
                        values (:navident,:navn,:epost, :enhet, :skjermet)
                        returning id
                     """,
                        mapOf(
                            "navident" to opprettSaksbehandler.navident,
                            "epost" to opprettSaksbehandler.epost.lowercase(getDefault()),
                            "navn" to opprettSaksbehandler.navn,
                            "enhet" to opprettSaksbehandler.enhet,
                            "skjermet" to kode6
                        )
                    ).map { row -> row.long("id") }.asSingle
                )
                opprettSaksbehandler.områder.forEach { område ->
                    tx.run(
                        queryOf(
                            """
                            insert into saksbehandler_omrade (saksbehandler_id, omrade_id)
                            select :saksbehandlerId, id from omrade
                            where omrade.ekstern_id = :omrade
                            on conflict do nothing
                            """,
                            mapOf("saksbehandlerId" to saksbehandlerId, "omrade" to område.eksternId)
                        ).asUpdate
                    )
                }
                saksbehandlerId!!
            }
            saksbehandlerRepository.finnSaksbehandlerMedId(saksbehandlerId)!!
        }
    }

    fun finnSaksbehandlerMedEpost(epost: String): Saksbehandler? {
        val saksbehandler = using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx.run(
                    queryOf(
                        "select * from saksbehandler where lower(epost) = lower(:epost) and skjermet = :skjermet",
                        mapOf("epost" to epost, "skjermet" to kode6)
                    ).map { row ->
                        Saksbehandler(
                            id = row.long("id"),
                            navident = row.stringOrNull("navident"),
                            navn = row.stringOrNull("navn"),
                            epost = row.string("epost").lowercase(getDefault()),
                            enhet = row.stringOrNull("enhet"),
                            områder = listOf(Områder.K9),
                            skjermet = row.boolean("skjermet"),
                            sistOppdatert = row.localDateTimeOrNull("sist_oppdatert")
                        )
                    }.asSingle
                )
            }
        }
        return saksbehandler
    }

    suspend fun hentAlleSaksbehandlere(): List<Saksbehandler> {
        val identer = using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    "select * from saksbehandler where skjermet = :skjermet",
                    mapOf("skjermet" to kode6)
                )
                    .map { row ->
                        val områder: List<Områder> = session.run(
                            queryOf(
                                "select o.ekstern_id from saksbehandler_omrade so inner join omrade o on so.omrade_id = o.id where so.saksbehandler_id = :id",
                                mapOf("id" to row.long("id"))
                            ).map { r -> Områder.fraEksternId(r.string(1)) }.asList
                        )

                        Saksbehandler(
                            id = row.long("id"),
                            navident = row.stringOrNull("navident"),
                            navn = row.stringOrNull("navn"),
                            epost = row.string("epost").lowercase(getDefault()),
                            enhet = row.stringOrNull("enhet"),
                            områder = områder,
                            skjermet = row.boolean("skjermet"),
                            sistOppdatert = row.localDateTimeOrNull("sist_oppdatert")
                        )
                    }.asList
            )
        }
        return identer
    }
}
