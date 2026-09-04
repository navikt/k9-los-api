package no.nav.k9.los.saksbehandleradmin

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import java.util.Locale.getDefault
import javax.sql.DataSource

class TestSaksbehandlerRepository(
    private val dataSource: DataSource,
    private val pepClient: IPepClient,
) {
    private val saksbehandlerRepository = SaksbehandlerRepository(dataSource, pepClient, TransactionalManager(dataSource))

    suspend fun opprettSaksbehandler(opprettSaksbehandler: OpprettSaksbehandler): Saksbehandler {
        val erSkjermet = pepClient.harTilgangTilKode6()
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
                            "skjermet" to erSkjermet
                        )
                    ).map { row -> row.long("id") }.asSingle
                )
                saksbehandlerId!!
            }
            saksbehandlerRepository.finnSaksbehandlerMedId(saksbehandlerId)!!
        }
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
                        Saksbehandler(
                            id = row.long("id"),
                            navident = row.stringOrNull("navident"),
                            navn = row.stringOrNull("navn"),
                            epost = row.string("epost").lowercase(getDefault()),
                            enhet = row.stringOrNull("enhet"),
                            sistOppdatert = row.localDateTimeOrNull("sist_oppdatert")
                        )
                    }.asSingle
                )
            }
        }
        return saksbehandler
    }

    suspend fun hentAlleSaksbehandlere(): List<Saksbehandler> {
        val skjermet = pepClient.harTilgangTilKode6()
        val identer = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    "select * from saksbehandler where skjermet = :skjermet",
                    mapOf("skjermet" to skjermet)
                )
                    .map { row ->
                        Saksbehandler(
                            id = row.long("id"),
                            navident = row.stringOrNull("navident"),
                            navn = row.stringOrNull("navn"),
                            epost = row.string("epost").lowercase(getDefault()),
                            enhet = row.stringOrNull("enhet"),
                            sistOppdatert = row.localDateTimeOrNull("sist_oppdatert")
                        )
                    }.asList
            )
        }
        return identer
    }
}
