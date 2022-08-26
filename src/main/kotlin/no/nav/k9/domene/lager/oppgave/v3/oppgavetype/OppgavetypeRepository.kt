package no.nav.k9.domene.lager.oppgave.v3.oppgavetype

import kotliquery.TransactionalSession
import org.slf4j.LoggerFactory
import javax.sql.DataSource

class OppgavetypeRepository(private val dataSource: DataSource) {
    fun hent(område: String, tx: TransactionalSession): Oppgavetyper {
        TODO("Not yet implemented")
    }

    private val log = LoggerFactory.getLogger(OppgavetypeRepository::class.java)
}