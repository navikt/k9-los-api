package no.nav.k9.los.repository

import no.nav.k9.los.aksjonspunktbehandling.objectMapper
import no.nav.k9.los.integrasjon.pdl.PdlService
import org.junit.jupiter.api.Test

class OppgaveRepositoryTest {

    @Test
    fun `Skal deserialisere`() {

        val queryRequest = PdlService.QueryRequest(
            getStringFromResource("/pdl/hentPerson.graphql"),
            mapOf("ident" to "Attributt.ident.value")
        )

        println(objectMapper().writeValueAsString(queryRequest))

    }

    private fun getStringFromResource(path: String) =
        OppgaveRepositoryTest::class.java.getResourceAsStream(path).bufferedReader().use { it.readText() }
}
