package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import io.opentelemetry.instrumentation.annotations.WithSpan
import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import org.slf4j.LoggerFactory

class OppgaveV3PartisjonertRepository(val oppgavetypeRepository: OppgavetypeRepository) {

    private val log = LoggerFactory.getLogger(OppgaveV3PartisjonertRepository::class.java)

    @WithSpan
    fun ajourhold(oppgave: OppgaveV3, tx: TransactionalSession) {
        oppdaterOppgaveV3(oppgave, tx)
        oppdaterOppgavefeltverdier(oppgave, tx)
    }

    private fun oppdaterOppgaveV3(oppgave: OppgaveV3, tx: TransactionalSession) {
        val eksisterendeOppgave = hentOppgave(oppgave, tx)
        if (eksisterendeOppgave == null) {
            nyOppgave(oppgave, tx)
        } else {
            oppdaterOppgave(oppgave, tx)
        }
    }

    private fun oppdaterOppgavefeltverdier(oppgave: OppgaveV3, tx: TransactionalSession) {
        val eksisterendeFelter = hentFeltverdier(oppgave, tx)
        val nyeFelter = oppgave.felter
        
        if (erForskjellige(eksisterendeFelter, nyeFelter)) {
            slettOppgavefeltverdier(oppgave, tx)
            nyeOppgavefeltverdier(oppgave, tx)
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
        oppgave: OppgaveV3,
        tx: TransactionalSession
    ): OppgaveV3? {
        return tx.run(
            queryOf(
                """
                select * from oppgave_v3_part where oppgave_ekstern_id = :oppgave_ekstern_id
                """.trimIndent(),
                mapOf("oppgave_ekstern_id" to oppgave.eksternId)
            ).map { row ->
                OppgaveV3(
                    eksternId = row.string("oppgave_ekstern_id"),
                    eksternVersjon = row.string("oppgave_ekstern_versjon"),
                    oppgavetype = oppgave.oppgavetype,
                    status = Oppgavestatus.valueOf(row.string("oppgavestatus")),
                    endretTidspunkt = oppgave.endretTidspunkt,
                    reservasjonsnøkkel = row.string("reservasjonsnokkel"),
                    felter = hentFeltverdier(oppgave, tx),
                    aktiv = true,
                    kildeområde = oppgave.kildeområde
                )
            }.asSingle)
    }

    private fun hentFeltverdier(
        oppgave: OppgaveV3,
        tx: TransactionalSession
    ): List<OppgaveFeltverdi> {
        return tx.run(
            queryOf(
                """
                    select * from oppgavefelt_verdi_part where oppgave_ekstern_id = :oppgave_ekstern_id
                """.trimIndent(),
                mapOf(
                    "oppgave_ekstern_id" to oppgave.eksternId
                )
            ).map { row ->
                OppgaveFeltverdi(
                    oppgavefelt = oppgave.hentFelt(row.string("feltdefinisjon_ekstern_id")),
                    verdi = row.string("verdi"),
                    verdiBigInt = row.longOrNull("verdi_bigint"),
                )
            }.asList
        )
    }

    private fun nyOppgave(
        oppgave: OppgaveV3,
        tx: TransactionalSession
    ) {
        tx.run(
            queryOf(
                """
                    insert into oppgave_v3_part(oppgave_ekstern_id, oppgave_ekstern_versjon, oppgavetype_ekstern_id, reservasjonsnokkel, endret_tidspunkt, oppgavestatus, ferdigstilt_dato)
                    VALUES (:oppgave_ekstern_id, :oppgave_ekstern_versjon, :oppgavetype_ekstern_id, :reservasjonsnokkel, :endret_tidspunkt, :oppgavestatus, :ferdigstilt_dato)
                """.trimIndent(),
                mapOf(
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
        oppgave: OppgaveV3,
        tx: TransactionalSession
    ) {
        tx.run(
            queryOf(
                """
                update oppgave_v3_part
                set
                    oppgave_ekstern_versjon = :oppgave_ekstern_versjon,
                    oppgavetype_ekstern_id = :oppgavetype_ekstern_id,
                    reservasjonsnokkel = :reservasjonsnokkel,
                    endret_tidspunkt = :endret_tidspunkt,
                    oppgavestatus = :oppgavestatus,
                    ferdigstilt_dato = :ferdigstilt_dato
                where oppgave_ekstern_id = :oppgave_ekstern_id
                """.trimIndent(),
                mapOf(
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

    private fun nyeOppgavefeltverdier(oppgave: OppgaveV3, tx: TransactionalSession) {
        if (oppgave.felter.isEmpty()) {
            return
        }
        
        tx.batchPreparedNamedStatement(
            """
                insert into oppgavefelt_verdi_part(oppgave_ekstern_id, oppgave_ekstern_versjon, omrade_ekstern_id, oppgavetype_ekstern_id, feltdefinisjon_ekstern_id, verdi, verdi_bigint, oppgavestatus, ferdigstilt_dato)
                        VALUES (:oppgave_ekstern_id, :oppgave_ekstern_versjon, :omrade_ekstern_id, :oppgavetype_ekstern_id, :feltdefinisjon_ekstern_id, :verdi, :verdi_bigint, :oppgavestatus, :ferdigstilt_dato)
            """.trimIndent(),
            oppgave.felter.map {
                mapOf(
                    "oppgave_ekstern_id" to oppgave.eksternId,
                    "oppgave_ekstern_versjon" to oppgave.eksternVersjon,
                    "omrade_ekstern_id" to it.oppgavefelt.feltDefinisjon.område.eksternId,
                    "oppgavetype_ekstern_id" to oppgave.oppgavetype.eksternId,
                    "feltdefinisjon_ekstern_id" to it.oppgavefelt.feltDefinisjon.eksternId,
                    "verdi" to it.verdi,
                    "verdi_bigint" to it.verdiBigInt,
                    "oppgavestatus" to oppgave.status.kode,
                    "ferdigstilt_dato" to if (oppgave.status == Oppgavestatus.LUKKET) oppgave.endretTidspunkt.toLocalDate() else null,
                )
            }
        )
    }

    private fun slettOppgavefeltverdier(oppgave: OppgaveV3, tx: TransactionalSession) {
        tx.run(
            queryOf(
                "delete from oppgavefelt_verdi_part where oppgave_ekstern_id = :oppgave_ekstern_id",
                mapOf(
                    "oppgave_ekstern_id" to oppgave.eksternId
                )
            ).asUpdate,
        )
    }
}
