package no.nav.k9.los.forvaltning

import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstMedOmråde
import no.nav.k9.los.infrastruktur.idtoken.IdTokenLocal
import no.nav.k9.los.kodeverk.Fagsystem
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class DriftstilgangTest {
    @Test
    fun `bare K9 drift får K9 fagsystemer og UNG har ingen fallback`() {
        for (område in Områder.entries) {
            for (drift in listOf(false, true)) {
                val bruker = BrukerkontekstMedOmråde(
                    område = område,
                    navIdent = "Z123456",
                    idToken = IdTokenLocal(),
                    harBasisTilgang = true,
                    harTilgangTilKode6 = false,
                    erOppgavestyrer = false,
                    harTilgangTilReserveringAvOppgaver = false,
                    harDriftstilgang = drift,
                )
                for (system in Fagsystem.entries) {
                    if (område == Områder.K9 && drift && system !in setOf(Fagsystem.UNGSAK, Fagsystem.UNGTILBAKE)) {
                        bruker.krevFagsystem(system)
                        bruker.krevK9Drift()
                    } else {
                        assertFailsWith<SecurityException> { bruker.krevFagsystem(system) }
                    }
                }
            }
        }
    }
}
