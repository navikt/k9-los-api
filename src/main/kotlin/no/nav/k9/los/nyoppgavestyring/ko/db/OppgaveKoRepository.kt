package no.nav.k9.los.nyoppgavestyring.ko.db

import com.fasterxml.jackson.databind.ObjectMapper
import kotliquery.*
import no.nav.k9.los.nyoppgavestyring.ko.dto.OppgaveKo
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.utils.LosObjectMapper
import java.time.LocalDateTime
import javax.sql.DataSource

class OppgaveKoRepository(val datasource: DataSource) {

    companion object {
        val objectMapper = LosObjectMapper.instance
    }

    private val standardOppgaveString: String by lazy {
        val standardOppgaveQuery = objectMapper.readValue(
            OppgaveKoRepository::class.java.getResource("/los/standard-ko.json")!!.readText(),
            OppgaveQuery::class.java
        )
        objectMapper.writeValueAsString(standardOppgaveQuery)
    }

    fun hentListe(kode6: Boolean): List<OppgaveKo> {
        return using(sessionOf(datasource)) {
            it.transaction { tx -> hentListe(tx, kode6) }
        }
    }

    fun hentListe(tx: TransactionalSession, kode6: Boolean): List<OppgaveKo> {
        return tx.run(
            queryOf(
                """SELECT id, versjon, tittel, beskrivelse, query, fritt_valg_av_oppgave, endret_tidspunkt, kode6 
                    FROM OPPGAVEKO_V3 WHERE kode6 = :kode6""",
                mapOf("kode6" to kode6)
            ).map { row -> row.tilOppgaveKo(objectMapper, tx) }.asList
        )

    }

    fun hentUavhengigAvSkjerming(oppgaveKoId: Long): Pair<OppgaveKo, Boolean> {
        return using(sessionOf(datasource)) {
            it.transaction { tx -> hentUavhengigAvSkjerming(tx, oppgaveKoId) }
        }
    }

    fun hentUavhengigAvSkjerming(tx: TransactionalSession, oppgaveKoId: Long): Pair<OppgaveKo, Boolean> {
        return tx.run(
            queryOf(
                """SELECT id, versjon, tittel, beskrivelse, query, fritt_valg_av_oppgave, endret_tidspunkt, kode6
                        FROM OPPGAVEKO_V3 
                        WHERE id = :id""",
                mapOf(
                    "id" to oppgaveKoId
                )
            ).map { it.tilOppgaveKo(objectMapper, tx) to it.boolean("kode6") }.asSingle)
            ?: throw IllegalStateException("Feil ved henting av oppgavekø: $oppgaveKoId")
    }

    fun hent(oppgaveKoId: Long, kode6: Boolean): OppgaveKo {
        return using(sessionOf(datasource)) {
            it.transaction { tx -> hent(tx, oppgaveKoId, kode6) }
        }
    }

    fun hent(tx: TransactionalSession, oppgaveKoId: Long, kode6: Boolean): OppgaveKo {
        return tx.run(
            queryOf(
                """SELECT id, versjon, tittel, beskrivelse, query, fritt_valg_av_oppgave, endret_tidspunkt, kode6
                        FROM OPPGAVEKO_V3 
                        WHERE id = :id AND kode6 = :kode6""",
                mapOf(
                    "id" to oppgaveKoId,
                    "kode6" to kode6
                )
            ).map { it.tilOppgaveKo(objectMapper, tx) }.asSingle
        ) ?: throw IllegalStateException("Feil ved henting av oppgavekø: $oppgaveKoId")
    }

    fun Row.tilOppgaveKo(objectMapper: ObjectMapper, tx: TransactionalSession): OppgaveKo {
        return OppgaveKo(
            id = long("id"),
            versjon = long("versjon"),
            tittel = string("tittel"),
            beskrivelse = string("beskrivelse"),
            oppgaveQuery = objectMapper.readValue(string("query"), OppgaveQuery::class.java),
            frittValgAvOppgave = boolean("fritt_valg_av_oppgave"),
            saksbehandlere = hentKoSaksbehandlere(tx, long("id")),
            endretTidspunkt = localDateTimeOrNull("endret_tidspunkt")
        )
    }

    fun leggTil(tittel: String, kode6: Boolean): OppgaveKo {
        return using(sessionOf(datasource)) { it ->
            it.transaction { tx -> leggTil(tx, tittel, kode6) }
        }
    }

    fun leggTil(tx: TransactionalSession, tittel: String, kode6: Boolean): OppgaveKo {
        val oppgaveKoId = tx.run(
            queryOf(
                """
                INSERT INTO OPPGAVEKO_V3 (versjon, tittel, beskrivelse, query, fritt_valg_av_oppgave, endret_tidspunkt, kode6) 
                VALUES (0, :tittel, '', :query, false, :endret_tidspunkt, :kode6) RETURNING ID""",
                mapOf(
                    "tittel" to tittel,
                    "query" to standardOppgaveString,
                    "endret_tidspunkt" to LocalDateTime.now(),
                    "kode6" to kode6
                )
            ).map { row -> row.long(1) }.asSingle
        ) ?: throw IllegalStateException("Feil ved opprettelse av ny oppgavekø.")
        return hent(tx, oppgaveKoId, kode6)
    }

    fun endre(oppgaveKo: OppgaveKo, kode6: Boolean): OppgaveKo {
        return using(sessionOf(datasource)) { it ->
            it.transaction { tx -> endre(tx, oppgaveKo, kode6) }
        }
    }

    fun endre(tx: TransactionalSession, oppgaveKo: OppgaveKo, kode6: Boolean): OppgaveKo {
        if (oppgaveKo.id == null) {
            throw IllegalArgumentException("Kan ikke oppdatere oppgavekø uten ID.")
        }

        val rows = tx.run(
            queryOf(
                """
                    UPDATE OPPGAVEKO_V3
                    SET versjon = :nyVersjon,
                      tittel = :tittel,
                      beskrivelse = :beskrivelse,
                      query = :query,
                      fritt_valg_av_oppgave = :frittValgAvOppgave,
                      endret_tidspunkt = :endret_tidspunkt
                    WHERE id = :id AND versjon = :gammelVersjon
                """.trimIndent(),
                mapOf(
                    "id" to oppgaveKo.id,
                    "nyVersjon" to (oppgaveKo.versjon + 1),
                    "gammelVersjon" to oppgaveKo.versjon,
                    "tittel" to oppgaveKo.tittel,
                    "beskrivelse" to oppgaveKo.beskrivelse,
                    "query" to objectMapper.writeValueAsString(oppgaveKo.oppgaveQuery),
                    "frittValgAvOppgave" to oppgaveKo.frittValgAvOppgave,
                    "endret_tidspunkt" to LocalDateTime.now()
                )
            ).asUpdate
        )

        if (rows != 1) {
            throw IllegalStateException("Feil ved oppdatering av oppgavekø: ${oppgaveKo.id}, rows: ${rows}")
        }

        lagreKoSaksbehandlere(tx, oppgaveKo)

        return hent(tx, oppgaveKo.id, kode6)
    }

    fun hentKoerMedOppgittSaksbehandler(
        tx: TransactionalSession,
        saksbehandler_epost: String,
        kode6: Boolean
    ): List<OppgaveKo> {
        return tx.run(
            queryOf(
                """
                    select id, versjon, tittel, beskrivelse, query, fritt_valg_av_oppgave, endret_tidspunkt 
                    from OPPGAVEKO_V3 ko
                    where kode6 = :kode6 AND
                    exists (
                        select *
                        from oppgaveko_saksbehandler s
                        where s.oppgaveko_v3_id = ko.id
                        and s.saksbehandler_epost = lower(:saksbehandler_epost)
                        )""",
                mapOf(
                    "saksbehandler_epost" to saksbehandler_epost,
                    "kode6" to kode6
                )
            ).map { row ->
                OppgaveKo(
                    id = row.long("id"),
                    versjon = row.long("versjon"),
                    tittel = row.string("tittel"),
                    beskrivelse = row.string("beskrivelse"),
                    oppgaveQuery = objectMapper.readValue(row.string("query"), OppgaveQuery::class.java),
                    frittValgAvOppgave = row.boolean("fritt_valg_av_oppgave"),
                    saksbehandlere = hentKoSaksbehandlere(tx, row.long("id")),
                    endretTidspunkt = row.localDateTimeOrNull("endret_tidspunkt")
                )
            }.asList
        )
    }

    private fun hentKoSaksbehandlere(tx: TransactionalSession, oppgavekoV3Id: Long): List<String> {
        return tx.run(
            queryOf(
                "SELECT saksbehandler_epost FROM OPPGAVEKO_SAKSBEHANDLER WHERE oppgaveko_v3_id = :oppgavekoV3Id",
                mapOf(
                    "oppgavekoV3Id" to oppgavekoV3Id
                )
            ).map { row -> row.string("saksbehandler_epost") }.asList
        )
    }

    private fun lagreKoSaksbehandlere(tx: TransactionalSession, oppgaveKo: OppgaveKo) {
        fjernAlleSaksbehandlereFraOppgaveKo(tx, oppgaveKo.id)
        oppgaveKo.saksbehandlere.forEach {
            val updated = tx.run(
                queryOf(
                    "INSERT INTO OPPGAVEKO_SAKSBEHANDLER (oppgaveko_v3_id, saksbehandler_epost) VALUES (:oppgavekoV3Id, :epost)",
                    mapOf(
                        "oppgavekoV3Id" to oppgaveKo.id,
                        "epost" to it
                    )
                ).asUpdate
            )
        }
    }

    fun slett(oppgaveKoId: Long) {
        using(sessionOf(datasource)) { it ->
            it.transaction { tx -> slett(tx, oppgaveKoId) }
        }
    }

    fun slett(tx: TransactionalSession, oppgaveKoId: Long) {
        fjernAlleSaksbehandlereFraOppgaveKo(tx, oppgaveKoId)
        tx.run(
            queryOf(
                "DELETE FROM OPPGAVEKO_V3 WHERE id = :id",
                mapOf(
                    "id" to oppgaveKoId
                )
            ).asUpdate
        )
    }

    private fun fjernAlleSaksbehandlereFraOppgaveKo(tx: TransactionalSession, oppgaveKoId: Long) {
        tx.run(
            queryOf(
                "DELETE FROM OPPGAVEKO_SAKSBEHANDLER WHERE oppgaveko_v3_id = :oppgavekoV3Id",
                mapOf(
                    "oppgavekoV3Id" to oppgaveKoId
                )
            ).asUpdate
        )
    }

    fun kopier(kopierFraOppgaveId: Long, tittel: String, taMedQuery: Boolean, taMedSaksbehandlere: Boolean, harkode6Tilgang: Boolean): OppgaveKo {
        return using(sessionOf(datasource)) { it ->
            it.transaction { tx ->
                kopier(
                    tx,
                    kopierFraOppgaveId = kopierFraOppgaveId,
                    tittel = tittel,
                    taMedQuery = taMedQuery,
                    taMedSaksbehandlere = taMedSaksbehandlere,
                    saksbehandlersSkjermetTilgang = harkode6Tilgang
                )
            }
        }
    }

    private fun kopier(
        tx: TransactionalSession,
        kopierFraOppgaveId: Long,
        tittel: String,
        taMedQuery: Boolean,
        taMedSaksbehandlere: Boolean,
        saksbehandlersSkjermetTilgang: Boolean
    ): OppgaveKo {
        val (gammelOppgaveKo, køensSkjermetTilgang) = hentUavhengigAvSkjerming(tx, kopierFraOppgaveId)
        if (saksbehandlersSkjermetTilgang != køensSkjermetTilgang) {
            throw IllegalStateException("Har ikke tilgang til å kopiere køen")
        }
        val nyOppgaveKo = leggTil(tx, tittel, køensSkjermetTilgang)

        val oppdatertNyOppgaveko = nyOppgaveKo.copy(
            oppgaveQuery = if (taMedQuery) gammelOppgaveKo.oppgaveQuery else nyOppgaveKo.oppgaveQuery,
            saksbehandlere = if (taMedSaksbehandlere) gammelOppgaveKo.saksbehandlere else nyOppgaveKo.saksbehandlere,
            beskrivelse = gammelOppgaveKo.beskrivelse,
            frittValgAvOppgave = gammelOppgaveKo.frittValgAvOppgave
        )

        return endre(tx, oppdatertNyOppgaveko, køensSkjermetTilgang)
    }
}