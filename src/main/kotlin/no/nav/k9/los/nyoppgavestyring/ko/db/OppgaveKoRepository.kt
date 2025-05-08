package no.nav.k9.los.nyoppgavestyring.ko.db

import com.fasterxml.jackson.databind.ObjectMapper
import kotliquery.*
import no.nav.k9.los.nyoppgavestyring.ko.dto.OppgaveKo
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import java.time.LocalDateTime
import javax.sql.DataSource

class OppgaveKoRepository(
    private val datasource: DataSource
) {

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
    private val kode6OppgaveString: String by lazy {
        val kode6OppgaveQuery = objectMapper.readValue(
            OppgaveKoRepository::class.java.getResource("/los/kode6-ko.json")!!.readText(),
            OppgaveQuery::class.java
        )
        objectMapper.writeValueAsString(kode6OppgaveQuery)
    }

    fun hentListe(skjermet: Boolean, medSaksbehandlere: Boolean = true): List<OppgaveKo> {
        return using(sessionOf(datasource)) {
            it.transaction { tx -> hentListe(tx = tx, medSaksbehandlere = medSaksbehandlere, skjermet = skjermet) }
        }
    }

    fun hentListe(tx: TransactionalSession, medSaksbehandlere: Boolean, skjermet: Boolean): List<OppgaveKo> {
        return tx.run(
            queryOf(
                """SELECT id, versjon, tittel, beskrivelse, query, fritt_valg_av_oppgave, endret_tidspunkt, skjermet 
                    FROM OPPGAVEKO_V3 WHERE skjermet = :medSkjermet""",
                mapOf("medSkjermet" to skjermet)
            ).map { row -> row.tilOppgaveKo(objectMapper, medSaksbehandlere, tx) }.asList
        )
    }

    fun hent(oppgaveKoId: Long, skjermet: Boolean): OppgaveKo {
        return using(sessionOf(datasource)) {
            it.transaction { tx -> hent(tx, oppgaveKoId, skjermet) }
        }
    }

    fun hent(tx: TransactionalSession, oppgaveKoId: Long, skjermet: Boolean): OppgaveKo {
        return tx.run(
            queryOf(
                """SELECT id, versjon, tittel, beskrivelse, query, fritt_valg_av_oppgave, endret_tidspunkt, skjermet
                        FROM OPPGAVEKO_V3 
                        WHERE id = :id AND skjermet = :skjermet""",
                mapOf(
                    "id" to oppgaveKoId,
                    "skjermet" to skjermet
                )
            ).map { it.tilOppgaveKo(objectMapper, true, tx) }.asSingle
        ) ?: throw IllegalStateException("Feil ved henting av oppgavekø: $oppgaveKoId")
    }

    fun hentInkluderKode6(oppgaveKoId: Long): OppgaveKo {
        return using(sessionOf(datasource)) {
            it.transaction { tx -> hentInkluderKode6(tx, oppgaveKoId).first }
        }
    }

    fun hentInkluderKode6(tx: TransactionalSession, oppgaveKoId: Long): Pair<OppgaveKo, Boolean> {
        return tx.run(
            queryOf(
                """SELECT id, versjon, tittel, beskrivelse, query, fritt_valg_av_oppgave, endret_tidspunkt, skjermet
                        FROM OPPGAVEKO_V3 
                        WHERE id = :id""",
                mapOf(
                    "id" to oppgaveKoId,
                )
            ).map { it.tilOppgaveKo(objectMapper, true, tx) to it.boolean("skjermet") }.asSingle
        ) ?: throw IllegalStateException("Feil ved henting av oppgavekø: $oppgaveKoId")
    }

    private fun Row.tilOppgaveKo(objectMapper: ObjectMapper, medSaksbehandlere: Boolean = true, tx: TransactionalSession): OppgaveKo {
        return OppgaveKo(
            id = long("id"),
            versjon = long("versjon"),
            tittel = string("tittel"),
            beskrivelse = string("beskrivelse"),
            oppgaveQuery = objectMapper.readValue(string("query"), OppgaveQuery::class.java),
            frittValgAvOppgave = boolean("fritt_valg_av_oppgave"),
            saksbehandlere = if (medSaksbehandlere) hentKoSaksbehandlere(tx, long("id")) else emptyList(),
            endretTidspunkt = localDateTimeOrNull("endret_tidspunkt"),
            skjermet = boolean("skjermet")
        )
    }

    fun leggTil(tittel: String, skjermet: Boolean): OppgaveKo {
        return using(sessionOf(datasource)) { session ->
            session.transaction { tx -> leggTil(tx, tittel, skjermet) }
        }
    }

    fun leggTil(tx: TransactionalSession, tittel: String, skjermet: Boolean): OppgaveKo {
        val queryString = if (skjermet) kode6OppgaveString else standardOppgaveString
        val oppgaveKoId = tx.run(
            queryOf(
                """
                INSERT INTO OPPGAVEKO_V3 (versjon, tittel, beskrivelse, query, fritt_valg_av_oppgave, endret_tidspunkt, skjermet) 
                VALUES (0, :tittel, '', :query, false, :endret_tidspunkt, :skjermet) RETURNING ID""",
                mapOf(
                    "tittel" to tittel,
                    "query" to queryString,
                    "endret_tidspunkt" to LocalDateTime.now(),
                    "skjermet" to skjermet
                )
            ).map { row -> row.long(1) }.asSingle
        ) ?: throw IllegalStateException("Feil ved opprettelse av ny oppgavekø.")
        return hent(tx, oppgaveKoId, skjermet)
    }

    fun endre(oppgaveKo: OppgaveKo, skjermet: Boolean): OppgaveKo {
        return using(sessionOf(datasource)) { session ->
            session.transaction { tx -> endre(tx, oppgaveKo, skjermet) }
        }
    }

    fun endre(tx: TransactionalSession, oppgaveKo: OppgaveKo, skjermet: Boolean): OppgaveKo {
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
                    WHERE id = :id AND versjon = :gammelVersjon AND skjermet = :skjermet
                """.trimIndent(),
                mapOf(
                    "id" to oppgaveKo.id,
                    "nyVersjon" to (oppgaveKo.versjon + 1),
                    "gammelVersjon" to oppgaveKo.versjon,
                    "tittel" to oppgaveKo.tittel,
                    "beskrivelse" to oppgaveKo.beskrivelse,
                    "query" to objectMapper.writeValueAsString(oppgaveKo.oppgaveQuery),
                    "frittValgAvOppgave" to oppgaveKo.frittValgAvOppgave,
                    "endret_tidspunkt" to LocalDateTime.now(),
                    "skjermet" to skjermet
                )
            ).asUpdate
        )

        if (rows != 1) {
            throw IllegalStateException("Feil ved oppdatering av oppgavekø: ${oppgaveKo.id}, rows: $rows")
        }

        lagreKoSaksbehandlere(tx, oppgaveKo)

        return hent(tx, oppgaveKo.id, skjermet)
    }

    fun hentKoerMedOppgittSaksbehandler(
        tx: TransactionalSession,
        saksbehandlerEpost: String,
        skjermet: Boolean,
        medSaksbehandlere: Boolean = true
    ): List<OppgaveKo> {
        return tx.run(
            queryOf(
                """
                    select id, versjon, tittel, beskrivelse, query, fritt_valg_av_oppgave, endret_tidspunkt, skjermet
                    from OPPGAVEKO_V3 ko
                    where skjermet = :skjermet AND
                    exists (
                        select *
                        from oppgaveko_saksbehandler s
                        where s.oppgaveko_v3_id = ko.id
                        and s.saksbehandler_epost = lower(:saksbehandler_epost)
                        )""",
                mapOf(
                    "saksbehandler_epost" to saksbehandlerEpost,
                    "skjermet" to skjermet
                )
            ).map { row ->
                row.tilOppgaveKo(objectMapper, medSaksbehandlere, tx)
            }.asList
        )
    }

    fun hentKoerMedOppgittSaksbehandler(
        tx: TransactionalSession,
        saksbehandlerId: Long,
        skjermet: Boolean,
        medSaksbehandlere: Boolean
    ): List<OppgaveKo> {
        return tx.run(
            queryOf(
                """
                    select id, versjon, tittel, beskrivelse, query, fritt_valg_av_oppgave, endret_tidspunkt, skjermet
                    from OPPGAVEKO_V3 ko
                    where skjermet = :skjermet AND      
                    exists (
                        select 1
                        from oppgaveko_saksbehandler os
                        inner join saksbehandler s on s.epost = os.saksbehandler_epost
                        where os.oppgaveko_v3_id = ko.id
                        and s.id = :saksbehandler_id
                        )""",
                mapOf(
                    "saksbehandler_id" to saksbehandlerId,
                    "skjermet" to skjermet
                )
            ).map { row ->
                row.tilOppgaveKo(objectMapper, medSaksbehandlere, tx)
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
            tx.run(
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
        using(sessionOf(datasource)) { session ->
            session.transaction { tx -> slett(tx, oppgaveKoId) }
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

    fun kopier(kopierFraOppgaveId: Long, tittel: String, taMedQuery: Boolean, taMedSaksbehandlere: Boolean, skjermet: Boolean): OppgaveKo {
        return using(sessionOf(datasource)) { session ->
            session.transaction { tx -> kopier(tx, kopierFraOppgaveId, tittel, taMedQuery, taMedSaksbehandlere, skjermet) }
        }
    }

    private fun kopier(
        tx: TransactionalSession,
        kopierFraOppgaveId: Long,
        tittel: String,
        taMedQuery: Boolean,
        taMedSaksbehandlere: Boolean,
        skjermet: Boolean
    ): OppgaveKo {
        val gammelOppgaveKo = hent(tx, kopierFraOppgaveId, skjermet)
        val nyOppgaveKo = leggTil(tx, tittel, skjermet)

        val oppdatertNyOppgaveko = nyOppgaveKo.copy(
            oppgaveQuery = if (taMedQuery) gammelOppgaveKo.oppgaveQuery else nyOppgaveKo.oppgaveQuery,
            saksbehandlere = if (taMedSaksbehandlere) gammelOppgaveKo.saksbehandlere else nyOppgaveKo.saksbehandlere,
            beskrivelse = gammelOppgaveKo.beskrivelse,
            frittValgAvOppgave = gammelOppgaveKo.frittValgAvOppgave
        )

        return endre(tx, oppdatertNyOppgaveko, skjermet)
    }
}