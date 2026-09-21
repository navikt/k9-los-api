package no.nav.k9.los.ko.db

import com.fasterxml.jackson.databind.ObjectMapper
import kotliquery.*
import no.nav.k9.los.ko.dto.OppgaveKo
import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRepository
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.kodeverk.PersonBeskyttelseType
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgaveuthenting.query.dto.query.EnkelOrderFelt
import no.nav.k9.los.oppgaveuthenting.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.oppgaveuthenting.query.mapping.EksternFeltverdiOperator
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import javax.sql.DataSource

class OppgaveKoRepository(
    private val datasource: DataSource,
    private val områdeRepository: OmrådeRepository
) {

    companion object {
        val objectMapper = LosObjectMapper.instance
        private val log = LoggerFactory.getLogger(OppgaveKoRepository::class.java)

        private const val OPPGAVEKO_SELECT =
            """SELECT ko.id, ko.versjon, ko.tittel, ko.beskrivelse, ko.query, ko.fritt_valg_av_oppgave, 
                      ko.endret_tidspunkt, ko.skjermet, o.ekstern_id as omrade_ekstern_id
               FROM OPPGAVEKO_V3 ko
               JOIN OMRADE o ON o.id = ko.omrade_id"""
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

    fun hentListe(område: Områder, skjermet: Boolean, medSaksbehandlere: Boolean = true): List<OppgaveKo> {
        return using(sessionOf(datasource)) {
            it.transaction { tx ->
                hentListe(tx = tx, medSaksbehandlere = medSaksbehandlere, skjermet = skjermet, område = område)
            }
        }
    }

    fun hentListe(
        tx: TransactionalSession,
        medSaksbehandlere: Boolean,
        skjermet: Boolean,
        område: Områder
    ): List<OppgaveKo> {
        return tx.run(
            queryOf(
                """$OPPGAVEKO_SELECT WHERE ko.skjermet = :medSkjermet AND o.ekstern_id = :omrade""",
                mapOf("medSkjermet" to skjermet, "omrade" to område.eksternId)
            ).map { row -> row.tilOppgaveKo(objectMapper, medSaksbehandlere, tx) }.asList
        )
    }

    fun hent(område: Områder, skjermet: Boolean, oppgaveKoId: Long): OppgaveKo {
        return using(sessionOf(datasource)) {
            it.transaction { tx -> hent(område, skjermet, oppgaveKoId, tx) }
        }
    }

    fun hent(område: Områder, skjermet: Boolean, oppgaveKoId: Long, tx: TransactionalSession): OppgaveKo {
        return tx.run(
            queryOf(
                """$OPPGAVEKO_SELECT
                        WHERE ko.id = :id AND ko.skjermet = :skjermet AND o.ekstern_id = :omrade""",
                mapOf(
                    "id" to oppgaveKoId,
                    "skjermet" to skjermet,
                    "omrade" to område.eksternId
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
                """$OPPGAVEKO_SELECT
                        WHERE ko.id = :id""",
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
            saksbehandlerIds = if (medSaksbehandlere) hentKoSaksbehandlerIds(tx, long("id")) else emptyList(),
            saksbehandlere = if (medSaksbehandlere) hentKoSaksbehandlere(tx, long("id")) else emptyList(),
            endretTidspunkt = localDateTimeOrNull("endret_tidspunkt"),
            skjermet = boolean("skjermet"),
            område = Områder.fraEksternId(string("omrade_ekstern_id"))
        )
    }

    fun leggTil(område: Områder, skjermet: Boolean, tittel: String): OppgaveKo {
        return using(sessionOf(datasource, returnGeneratedKey = true)) { session ->
            session.transaction { tx -> leggTil(tx, tittel, skjermet, område) }
        }
    }

    fun leggTil(tx: TransactionalSession, tittel: String, skjermet: Boolean, område: Områder): OppgaveKo {
        val personBeskyttelseType: PersonBeskyttelseType = if (skjermet) PersonBeskyttelseType.KODE6 else PersonBeskyttelseType.UGRADERT
        val oppgaveQuery = when (område) {
            Områder.K9 -> OppgaveQuery(
                filtere = listOf(
                    FeltverdiOppgavefilter(null, "oppgavestatus", EksternFeltverdiOperator.IN, listOf(Oppgavestatus.AAPEN.kode)),
                    FeltverdiOppgavefilter(null, "personbeskyttelse", EksternFeltverdiOperator.IN, listOf(personBeskyttelseType.kode)),
                    FeltverdiOppgavefilter(Områder.K9, "ytelsestype", EksternFeltverdiOperator.IN, listOf()),
                    FeltverdiOppgavefilter(Områder.K9, "liggerHosBeslutter", EksternFeltverdiOperator.IN, listOf()),
                ),
                order = listOf(
                    EnkelOrderFelt(Områder.K9, "mottattDato", true)
                )
            )
            Områder.AKTIVITETSPENGER -> OppgaveQuery(
                filtere = listOf(
                    FeltverdiOppgavefilter(null, "oppgavestatus", EksternFeltverdiOperator.IN, listOf(Oppgavestatus.AAPEN.kode)),
                    FeltverdiOppgavefilter(null, "personbeskyttelse", EksternFeltverdiOperator.IN, listOf(personBeskyttelseType.kode)),
                ),
                order = listOf(
                )
            )
        }
        val oppgaveKoId = tx.updateAndReturnGeneratedKey(
            queryOf(
                """
                INSERT INTO OPPGAVEKO_V3 (versjon, tittel, beskrivelse, query, fritt_valg_av_oppgave, endret_tidspunkt, skjermet, omrade_id) 
                VALUES (0, :tittel, '', :query, false, :endret_tidspunkt, :skjermet, :omradeId)""",
                mapOf(
                    "tittel" to tittel,
                    "query" to LosObjectMapper.instance.writeValueAsString(oppgaveQuery),
                    "endret_tidspunkt" to LocalDateTime.now(),
                    "skjermet" to skjermet,
                    "omradeId" to områdeRepository.hentOmråde(område, tx).id
                )
            )
        ) ?: throw IllegalStateException("Feil ved opprettelse av ny oppgavekø.")
        return hent(område, skjermet, oppgaveKoId, tx)
    }

    fun endre(område: Områder, skjermet: Boolean, oppgaveKo: OppgaveKo): OppgaveKo {
        return using(sessionOf(datasource)) { session ->
            session.transaction { tx -> endre(område, skjermet, oppgaveKo, tx) }
        }
    }

    fun endre(område: Områder, skjermet: Boolean, oppgaveKo: OppgaveKo, tx: TransactionalSession): OppgaveKo {
        require(oppgaveKo.område == område && oppgaveKo.skjermet == skjermet) {
            "Køens område eller skjerming stemmer ikke med kallet"
        }
        val rows = tx.run(
            queryOf(
                """
                    UPDATE OPPGAVEKO_V3 ko
                    SET versjon = :nyVersjon,
                      tittel = :tittel,
                      beskrivelse = :beskrivelse,
                      query = :query,
                      fritt_valg_av_oppgave = :frittValgAvOppgave,
                      endret_tidspunkt = :endret_tidspunkt
                    FROM OMRADE o
                    WHERE o.id = ko.omrade_id
                      AND ko.id = :id
                      AND ko.versjon = :gammelVersjon
                      AND ko.skjermet = :skjermet
                      AND o.ekstern_id = :omrade
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
                    "skjermet" to skjermet,
                    "omrade" to område.eksternId
                )
            ).asUpdate
        )

        if (rows != 1) {
            val dbRow = tx.run(
                queryOf(
                    """SELECT ko.versjon, ko.skjermet, o.ekstern_id as omrade_ekstern_id
                       FROM OPPGAVEKO_V3 ko
                       JOIN OMRADE o ON o.id = ko.omrade_id
                       WHERE ko.id = :id""",
                    mapOf("id" to oppgaveKo.id)
                ).map { row ->
                    Triple(row.long("versjon"), row.boolean("skjermet"), row.string("omrade_ekstern_id"))
                }.asSingle
            )
            val feilmelding = when {
                dbRow == null -> "Oppgavekø ${oppgaveKo.id} finnes ikke i databasen"
                dbRow.third != område.eksternId -> "Område-mismatch for oppgavekø ${oppgaveKo.id}: kø tilhører område ${dbRow.third}, men kallet gjelder område ${område.eksternId}"
                dbRow.second != skjermet -> "Skjermet-mismatch for oppgavekø ${oppgaveKo.id}: kø har skjermet=${dbRow.second}, men innlogget bruker har skjermet=$skjermet"
                dbRow.first != oppgaveKo.versjon -> "Optimistisk låsing feilet for oppgavekø ${oppgaveKo.id}: kø har versjon=${dbRow.first}, men mottok versjon=${oppgaveKo.versjon}"
                else -> "Ukjent feil ved oppdatering av oppgavekø ${oppgaveKo.id}, rows: $rows"
            }
            log.warn(feilmelding)
            throw IllegalStateException(feilmelding)
        }

        lagreKoSaksbehandlere(tx, oppgaveKo)

        return hent(område, skjermet, oppgaveKo.id, tx)
    }

    fun hentKoerMedOppgittSaksbehandler(
        område: Områder,
        skjermet: Boolean,
        saksbehandlerId: Long,
        medSaksbehandlere: Boolean,
        tx: TransactionalSession
    ): List<OppgaveKo> {
        return tx.run(
            queryOf(
                """
                    $OPPGAVEKO_SELECT
                    where ko.skjermet = :skjermet AND
                    o.ekstern_id = :omrade AND      
                    exists (
                         select 1
                         from oppgaveko_saksbehandler os
                         join saksbehandler s on s.id = os.saksbehandler_id and s.skjermet = ko.skjermet
                         join saksbehandler_omrade so on so.saksbehandler_id = s.id and so.omrade_id = ko.omrade_id
                         where os.oppgaveko_v3_id = ko.id
                        and os.saksbehandler_id = :saksbehandler_id
                        )""",
                mapOf(
                    "saksbehandler_id" to saksbehandlerId,
                    "skjermet" to skjermet,
                    "omrade" to område.eksternId
                )
            ).map { row ->
                row.tilOppgaveKo(objectMapper, medSaksbehandlere, tx)
            }.asList
        )
    }

    fun fjernSaksbehandlerFraOmråde(tx: TransactionalSession, saksbehandlerId: Long, område: Områder) {
        // Lås og versjoner køene før medlemskap endres, slik at samtidige køendringer ikke gjeninnfører brukeren.
        val parametre = mapOf("saksbehandlerId" to saksbehandlerId, "omrade" to område.eksternId)
        tx.run(queryOf(
            """
            update oppgaveko_v3 ko set versjon = versjon + 1, endret_tidspunkt = localtimestamp
            from omrade o
            where o.id = ko.omrade_id and o.ekstern_id = :omrade
              and exists (select 1 from oppgaveko_saksbehandler os
                          where os.oppgaveko_v3_id = ko.id and os.saksbehandler_id = :saksbehandlerId)
            """.trimIndent(), parametre
        ).asUpdate)
        tx.run(queryOf(
            """
            delete from oppgaveko_saksbehandler os using oppgaveko_v3 ko, omrade o
            where os.oppgaveko_v3_id = ko.id and ko.omrade_id = o.id
              and o.ekstern_id = :omrade and os.saksbehandler_id = :saksbehandlerId
            """.trimIndent(), parametre
        ).asUpdate)
    }

    private fun hentKoSaksbehandlere(tx: TransactionalSession, oppgavekoV3Id: Long): List<String> {
        return tx.run(
            queryOf(
                """
                SELECT s.epost FROM oppgaveko_saksbehandler os
                INNER JOIN saksbehandler s ON s.id = os.saksbehandler_id
                INNER JOIN oppgaveko_v3 ko ON ko.id = os.oppgaveko_v3_id AND ko.skjermet = s.skjermet
                INNER JOIN saksbehandler_omrade so ON so.saksbehandler_id = s.id AND so.omrade_id = ko.omrade_id
                WHERE os.oppgaveko_v3_id = :oppgavekoV3Id
                """,
                mapOf(
                    "oppgavekoV3Id" to oppgavekoV3Id
                )
            ).map { row -> row.string("epost") }.asList
        )
    }

    private fun hentKoSaksbehandlerIds(tx: TransactionalSession, oppgavekoV3Id: Long): List<Long> {
        return tx.run(
            queryOf(
                """SELECT os.saksbehandler_id FROM oppgaveko_saksbehandler os
                   JOIN saksbehandler s ON s.id = os.saksbehandler_id
                   JOIN oppgaveko_v3 ko ON ko.id = os.oppgaveko_v3_id AND ko.skjermet = s.skjermet
                   JOIN saksbehandler_omrade so ON so.saksbehandler_id = s.id AND so.omrade_id = ko.omrade_id
                   WHERE os.oppgaveko_v3_id = :oppgavekoV3Id""",
                mapOf(
                    "oppgavekoV3Id" to oppgavekoV3Id
                )
            ).map { row -> row.long("saksbehandler_id") }.asList
        )
    }

    private fun lagreKoSaksbehandlere(tx: TransactionalSession, oppgaveKo: OppgaveKo) {
        fjernAlleSaksbehandlereFraOppgaveKo(tx, oppgaveKo.id)
        oppgaveKo.saksbehandlerIds.forEach { id ->
            val insertedRows = tx.run(
                queryOf(
                    """
                    INSERT INTO OPPGAVEKO_SAKSBEHANDLER (oppgaveko_v3_id, saksbehandler_id)
                    SELECT :oppgavekoV3Id, s.id FROM saksbehandler s
                    JOIN saksbehandler_omrade so ON so.saksbehandler_id = s.id
                    JOIN oppgaveko_v3 ko ON ko.omrade_id = so.omrade_id
                    WHERE s.id = :saksbehandlerId AND ko.id = :oppgavekoV3Id AND s.skjermet = ko.skjermet
                    """,
                    mapOf(
                        "oppgavekoV3Id" to oppgaveKo.id,
                        "saksbehandlerId" to id
                    )
                ).asUpdate
            )
            if (insertedRows == 0) {
                throw IllegalStateException("Saksbehandler finnes ikke i køens område og skjerming")
            }
        }
    }

    fun slett(område: Områder, skjermet: Boolean, oppgaveKoId: Long) {
        using(sessionOf(datasource)) { session ->
            session.transaction { tx -> slett(område, skjermet, oppgaveKoId, tx) }
        }
    }

    fun slett(område: Områder, skjermet: Boolean, oppgaveKoId: Long, tx: TransactionalSession) {
        hent(område, skjermet, oppgaveKoId, tx)
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

    fun kopier(
        område: Områder,
        skjermet: Boolean,
        kopierFraOppgaveId: Long,
        tittel: String,
        taMedQuery: Boolean,
        taMedSaksbehandlere: Boolean
    ): OppgaveKo {
        return using(sessionOf(datasource, returnGeneratedKey = true)) { session ->
            session.transaction { tx ->
                kopier(tx, kopierFraOppgaveId, tittel, taMedQuery, taMedSaksbehandlere, skjermet, område)
            }
        }
    }

    private fun kopier(
        tx: TransactionalSession,
        kopierFraOppgaveId: Long,
        tittel: String,
        taMedQuery: Boolean,
        taMedSaksbehandlere: Boolean,
        skjermet: Boolean,
        område: Områder
    ): OppgaveKo {
        val gammelOppgaveKo = hent(område, skjermet, kopierFraOppgaveId, tx)
        val nyOppgaveKo = leggTil(tx, tittel, skjermet, gammelOppgaveKo.område)

        val oppdatertNyOppgaveko = nyOppgaveKo.copy(
            oppgaveQuery = if (taMedQuery) gammelOppgaveKo.oppgaveQuery else nyOppgaveKo.oppgaveQuery,
            saksbehandlere = if (taMedSaksbehandlere) gammelOppgaveKo.saksbehandlere else nyOppgaveKo.saksbehandlere,
            saksbehandlerIds = if (taMedSaksbehandlere) gammelOppgaveKo.saksbehandlerIds else nyOppgaveKo.saksbehandlerIds,
            beskrivelse = gammelOppgaveKo.beskrivelse,
            frittValgAvOppgave = gammelOppgaveKo.frittValgAvOppgave
        )

        return endre(område, skjermet, oppdatertNyOppgaveko, tx)
    }
}
