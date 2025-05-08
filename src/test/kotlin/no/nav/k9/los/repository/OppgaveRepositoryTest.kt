package no.nav.k9.los.repository

import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.PdlService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import org.junit.jupiter.api.Test

class OppgaveRepositoryTest {

    @Test
    fun `Skal deserialisere`() {

        val queryRequest = PdlService.QueryRequest(
            getStringFromResource("/pdl/hentPerson.graphql"),
            mapOf("ident" to "Attributt.ident.value")
        )

        println(LosObjectMapper.instance.writeValueAsString(queryRequest))

    }

    private fun getStringFromResource(path: String) =
        OppgaveRepositoryTest::class.java.getResourceAsStream(path).bufferedReader().use { it.readText() }
}
