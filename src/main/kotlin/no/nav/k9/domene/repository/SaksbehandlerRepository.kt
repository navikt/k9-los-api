package no.nav.k9.domene.repository

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.aksjonspunktbehandling.objectMapper
import no.nav.k9.domene.modell.Saksbehandler
import no.nav.k9.integrasjon.abac.IPepClient
import no.nav.k9.tjenester.innsikt.Databasekall
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.atomic.LongAdder
import javax.sql.DataSource

class SaksbehandlerRepository(
    private val dataSource: DataSource,
    private val pepClient: IPepClient
) {
    private val log: Logger = LoggerFactory.getLogger(SaksbehandlerRepository::class.java)
    private suspend fun lagreMedId(
        id: String,
        f: (Saksbehandler?) -> Saksbehandler
    ) {
        val skjermet = pepClient.harTilgangTilKode6()
        Databasekall.map.computeIfAbsent(object{}.javaClass.name + object{}.javaClass.enclosingMethod.name){ LongAdder() }.increment()

        using(sessionOf(dataSource)) {
            it.transaction { tx ->
                val run = tx.run(
                    queryOf(
                        "select data from saksbehandler where saksbehandlerid = :saksbehandlerid and skjermet = :skjermet for update",
                        mapOf("saksbehandlerid" to id, "skjermet" to skjermet)
                    )
                        .map { row ->
                            row.stringOrNull("data")
                        }.asSingle
                )
                val forrige: Saksbehandler?
                val saksbehandler = if (!run.isNullOrEmpty()) {
                    forrige = objectMapper().readValue(run, Saksbehandler::class.java)
                    f(forrige)
                } else {
                    f(null)
                }

                val json = objectMapper().writeValueAsString(saksbehandler)
                tx.run(
                    queryOf(
                        """
                        insert into saksbehandler as k (saksbehandlerid,navn, epost, data, skjermet)
                        values (:saksbehandlerid,:navn,:epost, :data :: jsonb, :skjermet)
                        on conflict (epost) do update
                        set data = :data :: jsonb, 
                            saksbehandlerid = :saksbehandlerid,
                            navn = :navn,
                            skjermet = :skjermet
                     """,
                        mapOf(
                            "saksbehandlerid" to id,
                            "epost" to saksbehandler.epost,
                            "navn" to saksbehandler.navn,
                            "data" to json,
                            "skjermet" to skjermet
                        )
                    ).asUpdate
                )
            }
        }
    }

    private fun lagreMedIdIkkeTaHensyn(
        id: String,
        f: (Saksbehandler?) -> Saksbehandler
    ) {
        Databasekall.map.computeIfAbsent(object{}.javaClass.name + object{}.javaClass.enclosingMethod.name){LongAdder()}.increment()

        using(sessionOf(dataSource)) {
            it.transaction { tx ->
                val run = tx.run(
                    queryOf(
                        "select data from saksbehandler where saksbehandlerid = :saksbehandlerid for update",
                        mapOf("saksbehandlerid" to id)
                    )
                        .map { row ->
                            row.stringOrNull("data")
                        }.asSingle
                )
                val forrige: Saksbehandler?
                val saksbehandler = if (!run.isNullOrEmpty()) {
                    forrige = objectMapper().readValue(run, Saksbehandler::class.java)
                    f(forrige)
                } else {
                    f(null)
                }

                val json = objectMapper().writeValueAsString(saksbehandler)
                tx.run(
                    queryOf(
                        """
                        insert into saksbehandler as k (saksbehandlerid,navn, epost, data, skjermet)
                        values (:saksbehandlerid,:navn,:epost, :data :: jsonb, :skjermet)
                        on conflict (epost) do update
                        set data = :data :: jsonb, 
                            saksbehandlerid = :saksbehandlerid,
                            navn = :navn
                     """,
                        mapOf(
                            "saksbehandlerid" to id,
                            "epost" to saksbehandler.epost,
                            "navn" to saksbehandler.navn,
                            "data" to json
                        )
                    ).asUpdate
                )
            }
        }
    }

    private suspend fun lagreMedEpost(
        epost: String,
        f: (Saksbehandler?) -> Saksbehandler
    ) {
        val erSkjermet = pepClient.harTilgangTilKode6()
        Databasekall.map.computeIfAbsent(object{}.javaClass.name + object{}.javaClass.enclosingMethod.name){LongAdder()}.increment()

        using(sessionOf(dataSource)) {
            it.transaction { tx ->
                val run = tx.run(
                    queryOf(
                        "select data from saksbehandler where lower(epost) = lower(:epost) and skjermet = :skjermet for update",
                        mapOf("epost" to epost, "skjermet" to erSkjermet)
                    )
                        .map { row ->
                            row.stringOrNull("data")
                        }.asSingle
                )
                val forrige: Saksbehandler?
                val saksbehandler = if (!run.isNullOrEmpty()) {
                    forrige = objectMapper().readValue(run, Saksbehandler::class.java)
                    f(forrige)
                } else {
                    f(null)
                }

                val json = objectMapper().writeValueAsString(saksbehandler)
                tx.run(
                    queryOf(
                        """
                        insert into saksbehandler as k (saksbehandlerid, navn, epost, data, skjermet)
                        values (:saksbehandlerid,:navn,:epost, :data :: jsonb, :skjermet)
                        on conflict (epost) do update
                        set data = :data :: jsonb, 
                            saksbehandlerid = :saksbehandlerid,
                            navn = :navn,
                            skjermet = :skjermet
                     """,
                        mapOf(
                            "saksbehandlerid" to saksbehandler.brukerIdent,
                            "epost" to saksbehandler.epost.lowercase(Locale.getDefault()),
                            "navn" to saksbehandler.navn,
                            "data" to json,
                            "skjermet" to erSkjermet
                        )
                    ).asUpdate
                )
            }
        }
    }


    suspend fun leggTilReservasjon(saksbehandlerid: String?, reservasjon: UUID) {
        if (saksbehandlerid == null) {
            return
        }
        lagreMedId(saksbehandlerid) { saksbehandler ->
            saksbehandler!!.reservasjoner.add(reservasjon)
            saksbehandler
        }
    }

    suspend fun leggTilFlereReservasjoner(saksbehandlerid: String?, reservasjon: List<UUID>) {
        if (saksbehandlerid == null) {
            return
        }
        lagreMedId(saksbehandlerid) { saksbehandler ->
            saksbehandler!!.reservasjoner.addAll(reservasjon)
            saksbehandler
        }
    }

     fun fjernReservasjon(id: String?, reservasjon: UUID) {
        if (id == null) {
            return
        }
        if (finnSaksbehandlerMedIdentIkkeTaHensyn(id) != null) {
            lagreMedIdIkkeTaHensyn(id) { saksbehandler ->
                saksbehandler!!.reservasjoner.remove(reservasjon)
                saksbehandler
            }
        }
    }

     fun fjernReservasjonIkkeTaHensyn(id: String?, reservasjon: UUID) {
        if (id == null) {
            return
        }
        if (finnSaksbehandlerMedIdentIkkeTaHensyn(id) != null) {
            lagreMedIdIkkeTaHensyn(id) { saksbehandler ->
                saksbehandler!!.reservasjoner.remove(reservasjon)
                saksbehandler
            }
        }
    }

    suspend fun addSaksbehandler(saksbehandler: Saksbehandler) {
        lagreMedEpost(saksbehandler.epost) {
            if (it == null) {
                saksbehandler
            } else {
                it.brukerIdent = saksbehandler.brukerIdent
                it.epost = saksbehandler.epost.lowercase(Locale.getDefault())
                it.navn = saksbehandler.navn
                it.enhet = saksbehandler.enhet
                it
            }
        }
    }

    suspend fun finnSaksbehandlerMedEpost(epost: String): Saksbehandler? {
        val skjermet = pepClient.harTilgangTilKode6()
        Databasekall.map.computeIfAbsent(object{}.javaClass.name + object{}.javaClass.enclosingMethod.name){LongAdder()}.increment()

        val saksbehandler = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    "select * from saksbehandler where lower(epost) = lower(:epost) and skjermet = :skjermet",
                    mapOf("epost" to epost, "skjermet" to skjermet)
                )
                    .map { row ->
                        mapSaksbehandler(row)
                    }.asSingle
            )
        }
        return saksbehandler
    }

    suspend fun finnSaksbehandlerMedIdent(ident: String): Saksbehandler? {
        val skjermet = pepClient.harTilgangTilKode6()
        Databasekall.map.computeIfAbsent(object{}.javaClass.name + object{}.javaClass.enclosingMethod.name){LongAdder()}.increment()

        val saksbehandler = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    "select * from saksbehandler where lower(saksbehandlerid) = lower(:ident) and skjermet = :skjermet",
                    mapOf("ident" to ident, "skjermet" to skjermet)
                )
                    .map { row ->
                        mapSaksbehandler(row)
                    }.asSingle
            )
        }
        return saksbehandler
    }

    fun finnSaksbehandlerMedIdentIkkeTaHensyn(ident: String): Saksbehandler? {
        Databasekall.map.computeIfAbsent(object{}.javaClass.name + object{}.javaClass.enclosingMethod.name){LongAdder()}.increment()

        val saksbehandler = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    "select * from saksbehandler where lower(saksbehandlerid) = lower(:ident)",
                    mapOf("ident" to ident)
                )
                    .map { row ->
                        mapSaksbehandler(row)
                    }.asSingle
            )
        }
        return saksbehandler
    }

    suspend fun slettSaksbehandler(epost: String) {

        Databasekall.map.computeIfAbsent(object{}.javaClass.name + object{}.javaClass.enclosingMethod.name){LongAdder()}.increment()
        val skjermet = pepClient.harTilgangTilKode6()
        using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        """
                            delete from saksbehandler 
                            where lower(epost) = lower(:epost) and skjermet = :skjermet""",
                        mapOf("epost" to epost.lowercase(Locale.getDefault()), "skjermet" to skjermet)
                    ).asUpdate
                )
            }
        }
    }

    suspend fun hentAlleSaksbehandlere(): List<Saksbehandler> {
        Databasekall.map.computeIfAbsent(object{}.javaClass.name + object{}.javaClass.enclosingMethod.name){LongAdder()}.increment()
        val skjermet = pepClient.harTilgangTilKode6()
        val identer = using(sessionOf(dataSource)) {
            it.run(
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

    fun hentAlleSaksbehandlereIkkeTaHensyn(): List<Saksbehandler> {
        Databasekall.map.computeIfAbsent(object{}.javaClass.name + object{}.javaClass.enclosingMethod.name){LongAdder()}.increment()
        val identer = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    "select * from saksbehandler",
                    mapOf()
                )
                    .map { row ->
                        mapSaksbehandler(row)
                    }.asList
            )
        }
        return identer
    }

    private fun mapSaksbehandler(row: Row): Saksbehandler {
        val data = row.stringOrNull("data")
        return if (data == null) {
            Saksbehandler(
                row.stringOrNull("saksbehandlerid"),
                row.stringOrNull("navn"),
                row.string("epost").lowercase(Locale.getDefault()),
                reservasjoner = mutableSetOf(),
                enhet = null
            )
        } else {
            Saksbehandler(
                brukerIdent = objectMapper().readValue<Saksbehandler>(data).brukerIdent,
                navn = objectMapper().readValue<Saksbehandler>(data).navn,
                epost = row.string("epost").lowercase(Locale.getDefault()),
                reservasjoner = objectMapper().readValue<Saksbehandler>(data).reservasjoner,
                enhet = objectMapper().readValue<Saksbehandler>(data).enhet
            )
        }
    }
}
