package no.nav.k9.domene.lager.oppgave.v3.datatype

import org.slf4j.LoggerFactory
import javax.sql.DataSource

class DatatypeRepository(private val dataSource: DataSource) {

    private val log = LoggerFactory.getLogger(DatatypeRepository::class.java)

    fun hent(område: String): Datatyper {
        TODO()
    }
}