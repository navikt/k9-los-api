package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import io.opentelemetry.instrumentation.annotations.WithSpan
import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavetype
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgavefelt
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class PartisjonertOppgaveRepository(val oppgavetypeRepository: OppgavetypeRepository) {

    private val log = LoggerFactory.getLogger(PartisjonertOppgaveRepository::class.java)

    @WithSpan
    fun ajourhold(oppgave: OppgaveV3, tx: TransactionalSession) {
        val partisjonertOppgaveId = hentPartisjonertOppgaveId(oppgave, tx)
            ?: opprettPartisjonertOppgaveId(oppgave, tx)
        val partisjonertOppgave = hentOppgave(partisjonertOppgaveId, oppgave.oppgavetype, tx)
        if (partisjonertOppgave == null) {
            nyOppgave(partisjonertOppgaveId, oppgave, tx)
        } else {
            oppdaterOppgave(partisjonertOppgaveId, oppgave, tx)
        }
        oppdaterOppgavefeltverdier(partisjonertOppgaveId, oppgave, tx)
    }

    private fun hentPartisjonertOppgaveId(oppgave: OppgaveV3, tx: TransactionalSession): PartisjonertOppgaveId? {
        return tx.run(
            queryOf(
                """
                select id from oppgave_id_part where oppgave_ekstern_id = :oppgave_ekstern_id and oppgavetype_ekstern_id = :oppgavetype_ekstern_id
                """.trimIndent(),
                mapOf(
                    "oppgave_ekstern_id" to oppgave.eksternId,
                    "oppgavetype_ekstern_id" to oppgave.oppgavetype.eksternId
                )
            ).map { row ->
                PartisjonertOppgaveId(row.long("id"))
            }.asSingle
        )
    }

    private fun opprettPartisjonertOppgaveId(oppgave: OppgaveV3, tx: TransactionalSession): PartisjonertOppgaveId {
        return tx.run(
            queryOf(
                "insert into oppgave_id_part(oppgave_ekstern_id, oppgavetype_ekstern_id) values (:oppgave_ekstern_id, :oppgavetype_ekstern_id)",
                mapOf(
                    "oppgave_ekstern_id" to oppgave.eksternId,
                    "oppgavetype_ekstern_id" to oppgave.oppgavetype.eksternId
                )
            ).asUpdateAndReturnGeneratedKey
        )?.let { PartisjonertOppgaveId(it) }
            ?: throw IllegalStateException("Kunne ikke opprette partisjonert oppgaveId for oppgave ${oppgave.eksternId} og oppgavetype ${oppgave.oppgavetype.eksternId}")
    }

    private fun oppdaterOppgavefeltverdier(
        oppgaveId: PartisjonertOppgaveId,
        oppgave: OppgaveV3,
        tx: TransactionalSession
    ) {
        val eksisterendeFelter = hentFeltverdier(oppgaveId, oppgave.oppgavetype, tx)
        val nyeFelter = oppgave.felter

        if (erForskjellige(eksisterendeFelter, nyeFelter)) {
            slettOppgavefeltverdier(oppgaveId, tx)
            nyeOppgavefeltverdier(oppgaveId, oppgave, tx)
        }
    }

    private fun erForskjellige(
        eksisterendeFelter: List<OppgaveFeltverdi>,
        nyeFelter: List<OppgaveFeltverdi>
    ): Boolean {
        val mapSammenlignbareVerdier = { verdi: OppgaveFeltverdi ->
            Triple(
                verdi.oppgavefelt.feltDefinisjon.eksternId,
                verdi.verdi,
                verdi.verdiBigInt
            )
        }

        val eksisterende = eksisterendeFelter.map(mapSammenlignbareVerdier)
        val nye = nyeFelter.map(mapSammenlignbareVerdier)
        return eksisterendeFelter.size != nyeFelter.size ||
                !eksisterende.containsAll(nye) ||
                !nye.containsAll(eksisterende)
    }

    private fun hentOppgave(
        oppgaveId: PartisjonertOppgaveId,
        oppgavetype: Oppgavetype,
        tx: TransactionalSession
    ): OppgaveV3? {
        return tx.run(
            queryOf(
                """
                select * from oppgave_v3_part where id = :oppgave_id
                """.trimIndent(),
                mapOf(
                    "oppgave_id" to oppgaveId.id,
                )
            ).map { row ->
                OppgaveV3(
                    id = oppgaveId,
                    eksternId = row.string("oppgave_ekstern_id"),
                    eksternVersjon = row.string("oppgave_ekstern_versjon"),
                    oppgavetype = oppgavetype,
                    status = Oppgavestatus.valueOf(row.string("oppgavestatus")),
                    endretTidspunkt = row.localDateTime("endret_tidspunkt"),
                    reservasjonsnøkkel = row.string("reservasjonsnokkel"),
                    felter = hentFeltverdier(oppgaveId, oppgavetype, tx),
                    aktiv = true,
                    kildeområde = oppgavetype.område.eksternId,
                )
            }.asSingle
        )
    }

    private fun hentFeltverdier(
        oppgaveId: PartisjonertOppgaveId,
        oppgavetype: Oppgavetype,
        tx: TransactionalSession
    ): List<OppgaveFeltverdi> {
        return tx.run(
            queryOf(
                """
                    select * from oppgavefelt_verdi_part where oppgave_id = :oppgave_id
                """.trimIndent(),
                mapOf(
                    "oppgave_id" to oppgaveId.id
                )
            ).map { row ->
                OppgaveFeltverdi(
                    oppgavefelt = oppgavetype.hentFelt(row.string("feltdefinisjon_ekstern_id")),
                    verdi = row.string("verdi"),
                    verdiBigInt = row.longOrNull("verdi_bigint"),
                )
            }.asList
        )
    }

    private fun nyOppgave(
        partisjonertOppgaveId: PartisjonertOppgaveId,
        oppgave: OppgaveV3,
        tx: TransactionalSession
    ) {
        tx.run(
            queryOf(
                """
                    insert into oppgave_v3_part(id, oppgave_ekstern_id, oppgave_ekstern_versjon, oppgavetype_ekstern_id, reservasjonsnokkel, endret_tidspunkt, oppgavestatus, ferdigstilt_dato)
                    VALUES (:oppgave_id, :oppgave_ekstern_id, :oppgave_ekstern_versjon, :oppgavetype_ekstern_id, :reservasjonsnokkel, :endret_tidspunkt, :oppgavestatus, :ferdigstilt_dato)
                """.trimIndent(),
                mapOf(
                    "oppgave_id" to partisjonertOppgaveId.id,
                    "oppgave_ekstern_id" to oppgave.eksternId,
                    "oppgave_ekstern_versjon" to oppgave.eksternVersjon,
                    "oppgavetype_ekstern_id" to oppgave.oppgavetype.eksternId,
                    "reservasjonsnokkel" to oppgave.reservasjonsnøkkel,
                    "endret_tidspunkt" to oppgave.endretTidspunkt,
                    "oppgavestatus" to oppgave.status.kode,
                    "ferdigstilt_dato" to if (oppgave.status == Oppgavestatus.LUKKET) oppgave.endretTidspunkt.toLocalDate() else null,
                )
            ).asUpdate
        )
    }

    private fun oppdaterOppgave(
        partisjonertOppgaveId: PartisjonertOppgaveId,
        oppgave: OppgaveV3,
        tx: TransactionalSession
    ) {
        tx.run(
            queryOf(
                """
                update oppgave_v3_part
                set
                    oppgave_ekstern_versjon = :oppgave_ekstern_versjon,
                    reservasjonsnokkel = :reservasjonsnokkel,
                    endret_tidspunkt = :endret_tidspunkt,
                    oppgavestatus = :oppgavestatus,
                    ferdigstilt_dato = :ferdigstilt_dato
                where id = :oppgave_id
                """.trimIndent(),
                mapOf(
                    "oppgave_id" to partisjonertOppgaveId.id,
                    "oppgave_ekstern_versjon" to oppgave.eksternVersjon,
                    "reservasjonsnokkel" to oppgave.reservasjonsnøkkel,
                    "endret_tidspunkt" to oppgave.endretTidspunkt,
                    "oppgavestatus" to oppgave.status.kode,
                    "ferdigstilt_dato" to if (oppgave.status == Oppgavestatus.LUKKET) oppgave.endretTidspunkt.toLocalDate() else null,
                )
            ).asUpdate
        )
    }

    private fun nyeOppgavefeltverdier(oppgaveId: PartisjonertOppgaveId, oppgave: OppgaveV3, tx: TransactionalSession) {
        if (oppgave.felter.isEmpty()) {
            return
        }

        tx.batchPreparedNamedStatement(
            """
                insert into oppgavefelt_verdi_part(oppgave_id, feltdefinisjon_ekstern_id, verdi, verdi_bigint, oppgavestatus, ferdigstilt_dato)
                        VALUES (:oppgave_id, :feltdefinisjon_ekstern_id, :verdi, :verdi_bigint, :oppgavestatus, :ferdigstilt_dato)
            """.trimIndent(),
            oppgave.felter.map {
                mapOf(
                    "oppgave_id" to oppgaveId.id,
                    "feltdefinisjon_ekstern_id" to it.oppgavefelt.feltDefinisjon.eksternId,
                    "verdi" to it.verdi,
                    "verdi_bigint" to it.verdiBigInt,
                    "oppgavestatus" to oppgave.status.kode,
                    "ferdigstilt_dato" to if (oppgave.status == Oppgavestatus.LUKKET) oppgave.endretTidspunkt.toLocalDate() else null,
                )
            }
        )
    }

    private fun slettOppgavefeltverdier(oppgaveId: PartisjonertOppgaveId, tx: TransactionalSession) {
        tx.run(
            queryOf(
                "delete from oppgavefelt_verdi_part where oppgave_id = :oppgave_id",
                mapOf(
                    "oppgave_id" to oppgaveId.id
                )
            ).asUpdate,
        )
    }

    fun hentOppgaveEksternIdOgOppgavetype(
        oppgaveId: PartisjonertOppgaveId,
        tx: TransactionalSession
    ): Pair<String, String> {
        return tx.run(
            queryOf(
                """
                select * from oppgave_id_part where id = :oppgave_id
                """.trimIndent(),
                mapOf(
                    "oppgave_id" to oppgaveId.id,
                )
            ).map { row ->
                Pair(row.string("oppgave_ekstern_id"), row.string("oppgavetype_ekstern_id"))
            }.asSingle
        ) ?: throw IllegalStateException("Fant ikke oppgave med id $oppgaveId")
    }

    fun hentOppgaveForId(id: PartisjonertOppgaveId, tx: TransactionalSession, now: LocalDateTime = LocalDateTime.now()): Oppgave {
        val oppgave = tx.run(
                queryOf(
                    """
                select * 
                from oppgave_v3_part ov
                where ov.id = :id
            """.trimIndent(),
                    mapOf("id" to id.id)
                ).map { row ->
                    mapOppgave(row, now, tx)
                }.asSingle
            ) ?: throw IllegalStateException("Fant ikke oppgave med id $id")

            return oppgave
        }

    private fun mapOppgave(
        row: Row,
        now: LocalDateTime,
        tx: TransactionalSession
    ): Oppgave {
        val oppgavetypeEksternId = row.string("oppgavetype_ekstern_id")
        val oppgavetype = oppgavetypeRepository.hentOppgavetype("K9", oppgavetypeEksternId, tx)
        val oppgavefelter = hentOppgavefelter(tx, row.long("id"), oppgavetype)
        return Oppgave(
            eksternId = row.string("oppgave_ekstern_id"),
            eksternVersjon = row.string("oppgave_ekstern_versjon"),
            oppgavetype = oppgavetype,
            status = row.string("oppgavestatus"),
            endretTidspunkt = row.localDateTime("endret_tidspunkt"),
            felter = oppgavefelter,
            reservasjonsnøkkel = row.string("reservasjonsnokkel"),
        ).fyllDefaultverdier().utledTransienteFelter(now)
    }

    private fun hentOppgavefelter(tx: TransactionalSession, oppgaveId: Long, oppgavetype: Oppgavetype): List<Oppgavefelt> {
        return tx.run(
            queryOf(
                """
                select ov.feltdefinisjon_ekstern_id as ekstern_id, fd.liste_type, f.pakrevd, ov.verdi, ov.verdi_bigint
                from oppgavefelt_verdi_part ov
                inner join feltdefinisjon fd on ov.feltdefinisjon_ekstern_id = fd.ekstern_id
                inner join oppgavefelt f on fd.id = f.feltdefinisjon_id and f.oppgavetype_id = :oppgavetypeId
                where ov.oppgave_id = :oppgaveId
                order by ov.feltdefinisjon_ekstern_id
                """.trimIndent(),
                mapOf("oppgaveId" to oppgaveId, "oppgavetypeId" to oppgavetype.id)
            ).map { row ->
                Oppgavefelt(
                    eksternId = row.string("ekstern_id"),
                    område = "K9",
                    listetype = row.boolean("liste_type"),
                    påkrevd = row.boolean("pakrevd"),
                    verdi = row.string("verdi"),
                    verdiBigInt = row.longOrNull("verdi_bigint"),
                )
            }.asList
        )
    }
}
