package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import io.opentelemetry.instrumentation.annotations.WithSpan
import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import org.slf4j.LoggerFactory

class OppgavefeltverdiPartisjonertRepository(val oppgavetypeRepository: OppgavetypeRepository) {

    private val log = LoggerFactory.getLogger(OppgavefeltverdiPartisjonertRepository::class.java)

    @WithSpan
    fun ajourholdOppgavefeltverdier(oppgave: OppgaveV3, nyVersjon: Long, tx: TransactionalSession) {
        check(nyVersjon >= 0) { "Ny versjon må være 0 eller høyere: $nyVersjon" }
        checkNotNull(oppgave.id) { "Trenger oppgaveId når det skal gjøres oppdatering" }

        val eksisterendeFelter =
            hentFeltverdier(
                oppgave.id,
                oppgave,
                tx
            )
        val nyeFelter = oppgave.felter

        when {
            eksisterendeFelter.isEmpty() -> insertFelter(oppgave.id, oppgave, tx)
            eksisterendeFelter.size != nyeFelter.size || !eksisterendeFelter.containsAll(nyeFelter) || !nyeFelter.containsAll(eksisterendeFelter) -> {
                slettFelter(oppgave.id, tx)
                insertFelter(oppgave.id, oppgave, tx)
            }
        }

    }

    fun deaktiverOppgavefelter(eksisterendeId: OppgaveId, tx: TransactionalSession) {
        tx.run(
            queryOf(
                "update oppgavefelt_verdi_part set aktiv = false where oppgave_id = :oppgave_id",
                mapOf(
                    "oppgave_id" to eksisterendeId.id
                )
            ).asUpdate
        )
    }

    private fun hentFeltverdier(
        oppgaveId: OppgaveId,
        oppgave: OppgaveV3,
        tx: TransactionalSession
    ): List<OppgaveFeltverdi> {
        return tx.run(
            queryOf(
                """
                    select * from oppgavefelt_verdi_part where oppgave_id = :oppgaveId
                """.trimIndent(),
                mapOf("oppgaveId" to oppgaveId.id)
            ).map { row ->
                OppgaveFeltverdi(
                    oppgavefelt = oppgave.oppgavetype.oppgavefelter.first { oppgavefelt ->
                        oppgave.oppgavetype.eksternId == row.string("oppgavetype_ekstern_id")
                                && oppgavefelt.feltDefinisjon.eksternId == row.string("feltdefinisjon_ekstern_id")
                                && oppgavefelt.feltDefinisjon.område.eksternId == row.string("omrade_ekstern_id")
                    },
                    verdi = row.string("verdi"),
                    verdiBigInt = row.longOrNull("verdi_bigint"),
                )
            }.asList
        )
    }

    fun insertFelter(
        oppgaveId: OppgaveId,
        oppgave: OppgaveV3,
        tx: TransactionalSession
    ) {
        tx.batchPreparedNamedStatement(
            """
                insert into oppgavefelt_verdi_part(oppgave_id, omrade_ekstern_id, oppgavetype_ekstern_id, feltdefinisjon_ekstern_id, verdi, verdi_bigint, aktiv, oppgavestatus, ferdigstilt_dato)
                        VALUES (:oppgave_id, :omrade_ekstern_id, :oppgavetype_ekstern_id, :feltdefinisjon_ekstern_id, :verdi, :verdi_bigint, :aktiv, :oppgavestatus, :ferdigstilt_dato)
            """.trimIndent(),
            oppgave.felter.map {
                mapOf(
                    "oppgave_id" to oppgaveId.id,
                    "omrade_ekstern_id" to it.oppgavefelt.feltDefinisjon.område.eksternId,
                    "oppgavetype_ekstern_id" to oppgave.oppgavetype.eksternId,
                    "feltdefinisjon_ekstern_id" to it.oppgavefelt.feltDefinisjon.eksternId,
                    "verdi" to it.verdi,
                    "verdi_bigint" to it.verdiBigInt,
                    "aktiv" to oppgave.aktiv,
                    "oppgavestatus" to oppgave.status.kode,
                    "ferdigstilt_dato" to if (oppgave.status == Oppgavestatus.LUKKET) oppgave.endretTidspunkt else null,
                )
            }
        )
    }

    private fun slettFelter(oppgaveId: OppgaveId, tx: TransactionalSession) {
        tx.run(
            queryOf(
                "delete from oppgavefelt_verdi_part where oppgave_id = :oppgave_id",
                mapOf("oppgave_id" to oppgaveId.id)
            ).asUpdate,
        )
    }
}
