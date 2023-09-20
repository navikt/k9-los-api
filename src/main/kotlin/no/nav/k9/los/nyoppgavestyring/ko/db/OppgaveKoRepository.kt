package no.nav.k9.los.nyoppgavestyring.ko.db

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.nyoppgavestyring.ko.dto.OppgaveKo
import no.nav.k9.los.nyoppgavestyring.ko.dto.OppgaveKoListeDto
import no.nav.k9.los.nyoppgavestyring.ko.dto.OppgaveKoListeelement
import no.nav.k9.los.nyoppgavestyring.query.dto.query.*
import java.lang.IllegalArgumentException
import java.time.LocalDateTime
import javax.sql.DataSource

class OppgaveKoRepository(val datasource: DataSource) {

    fun hentListe(): OppgaveKoListeDto {
        return using(sessionOf(datasource)) { it ->
            it.transaction { tx -> hentListe(tx) }
        }
    }

    fun hentListe(tx: TransactionalSession): OppgaveKoListeDto {
        return OppgaveKoListeDto(
            tx.run(
                queryOf(
                    "SELECT id, tittel, endret_tidspunkt FROM OPPGAVEKO_V3"
                ).map{row -> OppgaveKoListeelement(
                    id = row.long("id"),
                    tittel = row.string("tittel"),
                    antallSaksbehandlere = hentKoSaksbehandlere(tx, row.long("id")).size,
                    sistEndret = row.localDateTimeOrNull("endret_tidspunkt")
                )}.asList
            )
        )
    }

    fun hent(oppgaveKoId: Long): OppgaveKo {
        return using(sessionOf(datasource)) { it ->
            it.transaction { tx -> hent(tx, oppgaveKoId) }
        }
    }

    fun hent(tx: TransactionalSession, oppgaveKoId: Long): OppgaveKo {
        val objectMapper = jacksonObjectMapper()
        return tx.run(
            queryOf(
                "SELECT id, versjon, tittel, beskrivelse, query, fritt_valg_av_oppgave, endret_tidspunkt FROM OPPGAVEKO_V3 WHERE id = :id",
                mapOf(
                    "id" to oppgaveKoId
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
            }.asSingle
        ) ?: throw IllegalStateException("Feil ved henting av oppgavekø: $oppgaveKoId")
    }

    fun leggTil(tittel: String): OppgaveKo {
        return using(sessionOf(datasource)) { it ->
            it.transaction { tx -> leggTil(tx, tittel) }
        }
    }

    fun leggTil(tx: TransactionalSession, tittel: String): OppgaveKo {
        val objectMapper = jacksonObjectMapper()
        val oppgaveKoId = tx.run(
            queryOf(
                """
                INSERT INTO OPPGAVEKO_V3 (versjon, tittel, beskrivelse, query, fritt_valg_av_oppgave, endret_tidspunkt) 
                VALUES (0, :tittel, '', :query, false, :endret_tidspunkt) RETURNING ID""",
                mapOf(
                    "tittel" to tittel,
                    "query" to objectMapper.writeValueAsString(OppgaveQuery()),
                    "endret_tidspunkt" to LocalDateTime.now()
                )
            ).map{row -> row.long(1)}.asSingle
        ) ?: throw IllegalStateException("Feil ved opprettelse av ny oppgavekø.")
        return hent(tx, oppgaveKoId);
    }

    fun endre(oppgaveKo: OppgaveKo): OppgaveKo {
        return using(sessionOf(datasource)) { it ->
            it.transaction { tx -> endre(tx, oppgaveKo) }
        }
    }

    fun endre(tx: TransactionalSession, oppgaveKo: OppgaveKo): OppgaveKo {
        if (oppgaveKo.id == null) {
            throw IllegalArgumentException("Kan ikke oppdatere oppgavekø uten ID.")
        }

        val objectMapper = jacksonObjectMapper()
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

        return hent(tx, oppgaveKo.id)
    }

    private fun hentKoSaksbehandlere(tx: TransactionalSession, oppgavekoV3Id: Long): List<String> {
        return tx.run(
            queryOf(
                "SELECT saksbehandler_epost FROM OPPGAVEKO_SAKSBEHANDLER WHERE oppgaveko_v3_id = :oppgavekoV3Id",
                mapOf(
                    "oppgavekoV3Id" to oppgavekoV3Id
                )
            ).map{row -> row.string("saksbehandler_epost")}.asList
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

    fun kopier(kopierFraOppgaveId: Long, tittel: String, taMedQuery: Boolean, taMedSaksbehandlere: Boolean): OppgaveKo {
        return using(sessionOf(datasource)) { it ->
            it.transaction { tx -> kopier(tx, kopierFraOppgaveId, tittel, taMedQuery, taMedSaksbehandlere) }
        }
    }

    private fun kopier(tx: TransactionalSession, kopierFraOppgaveId: Long, tittel: String, taMedQuery: Boolean, taMedSaksbehandlere: Boolean): OppgaveKo {
        val gammelOppgaveKo = hent(tx, kopierFraOppgaveId)
        val nyOppgaveKo = leggTil(tx, tittel)

        val oppdatertNyOppgaveko = nyOppgaveKo.copy(
            oppgaveQuery = if (taMedQuery) gammelOppgaveKo.oppgaveQuery else nyOppgaveKo.oppgaveQuery,
            saksbehandlere = if (taMedSaksbehandlere) gammelOppgaveKo.saksbehandlere else nyOppgaveKo.saksbehandlere,
            beskrivelse = gammelOppgaveKo.beskrivelse,
            frittValgAvOppgave = gammelOppgaveKo.frittValgAvOppgave
        )

        return endre(tx, oppdatertNyOppgaveko)
    }
}