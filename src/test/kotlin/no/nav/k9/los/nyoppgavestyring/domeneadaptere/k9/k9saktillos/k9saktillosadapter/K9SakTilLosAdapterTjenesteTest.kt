package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.k9saktillos.k9saktillosadapter

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domene.modell.K9SakModell
import no.nav.k9.los.domene.repository.BehandlingProsessEventK9Repository
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.util.*

class K9SakTilLosAdapterTjenesteTest : AbstractK9LosIntegrationTest() {

    @Disabled
    @Test
    fun `test avspilling av behandlings prosess events k9`() {
        val behandlingProsessEventK9Repository = get<BehandlingProsessEventK9Repository>()

        val behandlingProsessEventUUID = UUID.randomUUID()
        behandlingProsessEventK9Repository.lagre(behandlingProsessEventUUID) {
            return@lagre opprettK9SakModell()
        }

        //k9SakTilLosAdapterTjeneste.spillAvBehandlingProsessEventer()

        assert(størrelseErLik(6))
    }

    private fun opprettK9SakModell(): K9SakModell {
        return jacksonObjectMapper().registerModule(JavaTimeModule()).readValue(
            javaClass.getResource("/prosessEventEksempel.json")!!,
            K9SakModell::class.java
        )
    }

    private fun størrelseErLik(forventetStørrelse: Long) : Boolean {
        val resultatStørrelse = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """select count(*) from oppgave_v3"""
                ).map { row -> row.long(1) }.asSingle
            )
        }
        return resultatStørrelse == forventetStørrelse
    }

}