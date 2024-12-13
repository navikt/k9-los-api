package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.aktivvask

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.db.util.InClauseHjelper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.sql.DataSource
import kotlin.concurrent.thread
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class Aktivvask(private val dataSource: DataSource) {

    private val TRÅDNAVN = "aktivvask"

    private val log: Logger = LoggerFactory.getLogger(Aktivvask::class.java)


    fun kjørAktivvask() {

        thread(
            start = true,
            isDaemon = true,
            name = TRÅDNAVN
        ) {
            Thread.sleep(2.toDuration(DurationUnit.SECONDS).inWholeMilliseconds)

            val antallPrRunde = 100
            do {
                var antallMigrert = migrerInntil(antallPrRunde);
            } while (antallMigrert == antallPrRunde);

            log.info("Aktivvask ferdig")
        }

    }

    private fun migrerInntil(antallPrRunde: Int): Int {
        return using(sessionOf(dataSource)) { session -> session.transaction { tx ->
            val idLukketAktivOppgave : List<Long> =  tx.run(
                 queryOf(
                    "select id from oppgave_v3_aktiv where status = 'LUKKET' order by id limit :maxAntall",
                    mapOf("maxAntall" to antallPrRunde)
                )
                    .map { row -> row.long("id") }
                    .asList
            )

            if (!idLukketAktivOppgave.isEmpty()) {
                tx.run { queryOf("delete from oppgavefelt_verdi_aktiv where oppgave_id in ${InClauseHjelper.tilParameternavn(idLukketAktivOppgave, "id")}", InClauseHjelper.parameternavnTilVerdierMap(idLukketAktivOppgave, "id")).asUpdate }
                tx.run { queryOf("delete from oppgave_v3_aktiv where id in ${InClauseHjelper.tilParameternavn(idLukketAktivOppgave, "id")}", InClauseHjelper.parameternavnTilVerdierMap(idLukketAktivOppgave, "id")).asUpdate }
                log.info("Aktivvask slettet fra aktiv-tabellene for ${idLukketAktivOppgave.size} oppgaver")
            } else {
                log.info("Ingenting å slette fra aktiv-tabellene")
            }
            idLukketAktivOppgave.size
        }
        }
    }
}