package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import org.koin.test.KoinTest
import org.koin.test.get
import javax.sql.DataSource

class TestSaksbehandler: KoinTest {

    val datasource = get<DataSource>()
    val pepClient = mockk<IPepClient>(relaxed = true)
    val repo = SaksbehandlerRepository(
        datasource, pepClient = pepClient,
        transactionalManager = get(),
    )

    companion object {
        val SARA = Saksbehandler(
            id = 1,
            brukerIdent = "Z123456",
            navn = "Sara Saksbehandler",
            epost = "sara.saksbehandler@nav.no",
            reservasjoner = mutableSetOf(),
            enhet = "2830 NAV DRIFT"
        )

        val BIRGER_BESLUTTER = Saksbehandler(
            id = 2,
            brukerIdent = "Z654321",
            navn = "Birger Beslutter",
            epost = "birger.beslutter@nav.no",
            reservasjoner = mutableSetOf(),
            enhet = "2830 NAV DRIFT"
        )

        val KJERSTI_SKJERMET = Saksbehandler(
            id = 3,
            brukerIdent = "Z999999",
            navn = "Kjersti Skjermet",
            epost = "kjersti.skjermet@nav.no",
            reservasjoner = mutableSetOf(),
            enhet = "SKJERMET"
        )

    }

    fun init() {
        runBlocking {
            repo.addSaksbehandler(SARA)
            repo.addSaksbehandler(BIRGER_BESLUTTER)
            leggTilSkjermet()
        }
    }

    private suspend fun leggTilSkjermet() {
        coEvery { pepClient.harTilgangTilKode6() } returns true
        repo.addSaksbehandler(KJERSTI_SKJERMET)
        coEvery { pepClient.harTilgangTilKode6() } returns false
    }
}