package no.nav.k9.domene.lager.oppgave.v3.oppgavetype

import org.slf4j.LoggerFactory
import javax.sql.DataSource

class OppgavetypeRepository(private val dataSource: DataSource) {

    private val log = LoggerFactory.getLogger(OppgavetypeRepository::class.java)
}