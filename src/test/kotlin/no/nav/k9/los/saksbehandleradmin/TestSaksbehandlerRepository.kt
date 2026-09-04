package no.nav.k9.los.saksbehandleradmin

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.infrastruktur.abac.IPepClient
import java.util.Locale.getDefault
import javax.sql.DataSource

/**
 * Repository for test- og lokal utviklingsformål.
 * Skal ikke brukes i produksjonskode.
 *
 * Denne klassen finnes fordi tester og localSetup trenger å opprette
 * saksbehandlere med fullstendig informasjon (navident, navn, enhet),
 * mens produksjonsflyten kun oppretter saksbehandlere med epost.
 */
class TestSaksbehandlerRepository(
    private val dataSource: DataSource,
    private val pepClient: IPepClient,
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
