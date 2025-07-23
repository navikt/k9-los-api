package no.nav.k9.los.nyoppgavestyring.saksbehandleradmin

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.*
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
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
                    forrige = LosObjectMapper.instance.readValue(run, Saksbehandler::class.java)
                    f(forrige)
                } else {
                    f(null)
                }

                val json = LosObjectMapper.instance.writeValueAsString(saksbehandler)
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

    private fun lagreMedIdInkluderKode6(
        id: String,
        f: (Saksbehandler?) -> Saksbehandler
    ) {
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
                    forrige = LosObjectMapper.instance.readValue(run, Saksbehandler::class.java)
                    f(forrige)
                } else {
                    f(null)
                }

                val json = LosObjectMapper.instance.writeValueAsString(saksbehandler)
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

    suspend fun addSaksbehandler(saksbehandler: Saksbehandler) {
        lagreMedEpost(saksbehandler.epost) {
            if (it == null) {
                saksbehandler
            } else {
                it.id = saksbehandler.id
                it.brukerIdent = saksbehandler.brukerIdent
                it.epost = saksbehandler.epost.lowercase(Locale.getDefault())
                it.navn = saksbehandler.navn
                it.enhet = saksbehandler.enhet
                it
            }
        }
    }

    private suspend fun lagreMedEpost(
        epost: String,
        f: (Saksbehandler?) -> Saksbehandler
    ) {
        val erSkjermet = pepClient.harTilgangTilKode6()

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
                    forrige = LosObjectMapper.instance.readValue(run, Saksbehandler::class.java)
                    f(forrige)
                } else {
                    f(null)
                }

                val json = LosObjectMapper.instance.writeValueAsString(saksbehandler)
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
            loggLeggTilReservasjon(saksbehandlerid, listOf(reservasjon))
            saksbehandler
        }
    }

    suspend fun leggTilFlereReservasjoner(saksbehandlerid: String?, reservasjon: List<UUID>) {
        if (saksbehandlerid == null) {
            return
        }
        lagreMedId(saksbehandlerid) { saksbehandler ->
            saksbehandler!!.reservasjoner.addAll(reservasjon)
            loggLeggTilReservasjon(saksbehandlerid, reservasjon)
            saksbehandler
        }
    }

    fun fjernReservasjon(id: String?, reservasjon: UUID) {
        if (id == null) {
            return
        }
        if (finnSaksbehandlerMedIdentInkluderKode6(id) != null) {
            lagreMedIdInkluderKode6(id) { saksbehandler ->
                val fjernet = saksbehandler!!.reservasjoner.remove(reservasjon)
                loggFjernet(fjernet, id, reservasjon)
                saksbehandler
            }
        }
    }

    fun fjernReservasjonInkluderKode6(id: String?, reservasjon: UUID) {
        if (id == null) {
            return
        }
        if (finnSaksbehandlerMedIdentInkluderKode6(id) != null) {
            lagreMedIdInkluderKode6(id) { saksbehandler ->
                val fjernet = saksbehandler!!.reservasjoner.remove(reservasjon)
                loggFjernet(fjernet, id, reservasjon)
                saksbehandler
            }
        }
    }

    private fun loggLeggTilReservasjon(id: String, reservasjon: List<UUID>) {
        log.info("RESERVASJONDEBUG: Lagt til $id oppgave(r)=$reservasjon i saksbehandlertabell")
    }

    private fun loggFjernet(fjernet: Boolean, id: String, reservasjon: UUID) {
        if (fjernet) log.info("RESERVASJONDEBUG: Fjernet $id oppgave=${reservasjon} fra saksbehandlertabell")
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

    fun finnSaksbehandlerIdForIdent(ident: String): Long? {
        return using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                finnSaksbehandlerIdForIdent(ident, tx)
            }
        }
    }

    fun finnSaksbehandlerIdForIdent(ident: String, tx: TransactionalSession): Long? {
        return tx.run(
            queryOf(
                "select * from saksbehandler where lower(saksbehandlerid) = lower(:ident)",
                mapOf("ident" to ident)
            ).map { row ->
                row.longOrNull("id")
            }.asSingle
        )
    }

    suspend fun finnSaksbehandlerMedIdent(ident: String): Saksbehandler? {
        val skjermet = pepClient.harTilgangTilKode6()

        val saksbehandler = using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        "select * from saksbehandler where lower(saksbehandlerid) = lower(:ident) and skjermet = :skjermet",
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

    fun finnSaksbehandlerMedIdentInkluderKode6(ident: String): Saksbehandler? {
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

    fun finnSaksbehandlerMedIdentEkskluderKode6(ident: String): Saksbehandler? {
        val saksbehandler = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    "select * from saksbehandler where skjermet = false and lower(saksbehandlerid) = lower(:ident)",
                    mapOf("ident" to ident)
                )
                    .map { row ->
                        mapSaksbehandler(row)
                    }.asSingle
            )
        }
        return saksbehandler
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

        if (saksbehandlerId == null) { throw IllegalStateException("Fant ikke saksbehandler med epost $epost") }

        //Sletting av reservasjoner ligger her og ikke i reservasjonV3Repository, siden dette ikke er en del av "vanlig"
        //saksgang. Tanken var egentlig at reservasjoner og reservasjon_v3_endring ikke skulle slettes.
        tx.run(
            queryOf(
                """
                    delete from reservasjon_v3_endring where endret_av = :saksbehandlerId
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

    fun hentAlleSaksbehandlereEkskluderKode6(): List<Saksbehandler> {
        val identer = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    "select * from saksbehandler where skjermet = false",
                    mapOf()
                )
                    .map { row ->
                        mapSaksbehandler(row)
                    }.asList
            )
        }
        return identer
    }

    fun hentAlleSaksbehandlereInkluderKode6(): List<Saksbehandler> {
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
                row.long("id"),
                row.stringOrNull("saksbehandlerid"),
                row.stringOrNull("navn"),
                row.string("epost").lowercase(Locale.getDefault()),
                reservasjoner = mutableSetOf(),
                enhet = null
            )
        } else {
            val saksbehandler = LosObjectMapper.instance.readValue<Saksbehandler>(data)
            Saksbehandler(
                id = row.long("id"),
                brukerIdent = saksbehandler.brukerIdent,
                navn = saksbehandler.navn,
                epost = row.string("epost").lowercase(Locale.getDefault()),
                reservasjoner = saksbehandler.reservasjoner,
                enhet = saksbehandler.enhet
            )
        }
    }
}
