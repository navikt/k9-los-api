package no.nav.k9.los.domeneadaptere.k9.eventmottak

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.koin.test.KoinTest
import org.koin.test.get
import javax.sql.DataSource

class TestSaksbehandler: KoinTest {

    val datasource = get<DataSource>()
    val pepClient = mockk<IPepClient>(relaxed = true)
    val repo = SaksbehandlerRepository(
        datasource, pepClient = pepClient,
        transactionalManager = get(),
        områdeRepository = get(),
    )

    companion object {
        val SARA = Saksbehandler(
            id = 1,
            navident = "Z123456",
            navn = "Sara Saksbehandler",
            epost = "sara.saksbehandler@nav.no",
            enhet = "2830 NAV DRIFT",
            områder = listOf(Områder.K9)
        )

        val BIRGER_BESLUTTER = Saksbehandler(
            id = 2,
            navident = "Z654321",
            navn = "Birger Beslutter",
            epost = "birger.beslutter@nav.no",
            enhet = "2830 NAV DRIFT",
            områder = listOf(Områder.K9)
        )

        val KJERSTI_SKJERMET = Saksbehandler(
            id = 3,
            navident = "Z999999",
            navn = "Kjersti Skjermet",
            epost = "kjersti.skjermet@nav.no",
            enhet = "SKJERMET",
            områder = listOf(Områder.K9)
        )

    }

    fun init() {
        runBlocking {
            repo.addSaksbehandler(SARA.epost, Områder.K9)
            repo.vedlikeholdSaksbehandler(SARA)
            repo.addSaksbehandler(BIRGER_BESLUTTER.epost, Områder.K9)
            repo.vedlikeholdSaksbehandler(BIRGER_BESLUTTER)
            leggTilSkjermet()
        }
    }

    private suspend fun leggTilSkjermet() {
        coEvery { pepClient.harTilgangTilKode6() } returns true
        repo.addSaksbehandler(KJERSTI_SKJERMET.epost, Områder.K9)
        repo.vedlikeholdSaksbehandler(KJERSTI_SKJERMET)
        coEvery { pepClient.harTilgangTilKode6() } returns false
    }
}