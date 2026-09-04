package no.nav.k9.los.domeneadaptere.k9.eventmottak

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.saksbehandleradmin.OpprettSaksbehandler
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.TestSaksbehandlerRepository
import no.nav.k9.los.infrastruktur.abac.IPepClient
import org.koin.test.KoinTest
import org.koin.test.get
import javax.sql.DataSource

class TestSaksbehandler: KoinTest {

    val datasource = get<DataSource>()
    val pepClient = mockk<IPepClient>(relaxed = true)
    val repo = TestSaksbehandlerRepository(
        datasource, pepClient = pepClient,
    )

    companion object {
        val SARA = Saksbehandler(
            id = 1,
            navident = "Z123456",
            navn = "Sara Saksbehandler",
            epost = "sara.saksbehandler@nav.no",
            enhet = "2830 NAV DRIFT"
        )

        val BIRGER_BESLUTTER = Saksbehandler(
            id = 2,
            navident = "Z654321",
            navn = "Birger Beslutter",
            epost = "birger.beslutter@nav.no",
            enhet = "2830 NAV DRIFT"
        )

        val KJERSTI_SKJERMET = Saksbehandler(
            id = 3,
            navident = "Z999999",
            navn = "Kjersti Skjermet",
            epost = "kjersti.skjermet@nav.no",
            enhet = "SKJERMET"
        )

    }

    fun init() {
        runBlocking {
            repo.upsertSaksbehandler(SARA.tilOpprettSaksbehandler())
            repo.upsertSaksbehandler(BIRGER_BESLUTTER.tilOpprettSaksbehandler())
            leggTilSkjermet()
        }
    }

    private suspend fun leggTilSkjermet() {
        coEvery { pepClient.harTilgangTilKode6() } returns true
        repo.upsertSaksbehandler(KJERSTI_SKJERMET.tilOpprettSaksbehandler())
        coEvery { pepClient.harTilgangTilKode6() } returns false
    }

    private fun Saksbehandler.tilOpprettSaksbehandler() = OpprettSaksbehandler(
        navident = navident!!,
        navn = navn!!,
        epost = epost,
        enhet = enhet
    )
}
