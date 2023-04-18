package no.nav.k9.los.nyoppgavestyring.ko.db

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.nyoppgavestyring.ko.dto.OppgaveKo
import no.nav.k9.los.nyoppgavestyring.ko.dto.OppgaveKoListeDto
import no.nav.k9.los.nyoppgavestyring.ko.dto.OppgaveKoListeelement
import no.nav.k9.los.nyoppgavestyring.query.dto.felter.Oppgavefelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.*
import java.lang.IllegalArgumentException
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
                    "SELECT id, tittel FROM OPPGAVEKO_V3"
                ).map{row -> OppgaveKoListeelement(
                    row.long("id"),
                    row.string("tittel")
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
                "SELECT id, versjon, tittel, beskrivelse, query, fritt_valg_av_oppgave FROM OPPGAVEKO_V3 WHERE id = :id",
                mapOf(
                    "id" to oppgaveKoId
                )
            ).map{row -> OppgaveKo(
                row.long("id"),
                row.long("versjon"),
                row.string("tittel"),
                row.string("beskrivelse"),
                objectMapper.readValue(row.string("query"), OppgaveQuery::class.java),
                row.boolean("fritt_valg_av_oppgave"),
                hentKoSaksbehandlere(tx, row.long("id"))
            )}.asSingle
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
                INSERT INTO OPPGAVEKO_V3 (versjon, tittel, beskrivelse, query, fritt_valg_av_oppgave) 
                VALUES (0, :tittel, '', :query, false) RETURNING ID""",
                mapOf(
                    "tittel" to tittel,
                    "query" to objectMapper.writeValueAsString(OppgaveQuery())
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
                      fritt_valg_av_oppgave = :frittValgAvOppgave
                    WHERE id = :id AND versjon = :gammelVersjon
                """.trimIndent(),
                mapOf(
                    "id" to oppgaveKo.id,
                    "nyVersjon" to (oppgaveKo.versjon + 1),
                    "gammelVersjon" to oppgaveKo.versjon,
                    "tittel" to oppgaveKo.tittel,
                    "beskrivelse" to oppgaveKo.beskrivelse,
                    "query" to objectMapper.writeValueAsString(OppgaveQuery()),
                    "frittValgAvOppgave" to oppgaveKo.frittValgAvOppgave
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
            ).map{row -> row.string("epost")}.asList
        )
    }

    private fun lagreKoSaksbehandlere(tx: TransactionalSession, oppgaveKo: OppgaveKo) {
        tx.run(
            queryOf(
                "DELETE FROM OPPGAVEKO_SAKSBEHANDLER WHERE oppgaveko_v3_id = :oppgavekoV3Id",
                mapOf(
                    "oppgavekoV3Id" to oppgaveKo.id
                )
            ).asUpdate
        )
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
}