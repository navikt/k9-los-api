package no.nav.k9.domene.lager.oppgave.v3.oppgave

import org.slf4j.LoggerFactory
import javax.sql.DataSource

class OppgaveV3Repository(private val dataSource: DataSource) {

    private val log = LoggerFactory.getLogger(OppgaveV3Repository::class.java)

}