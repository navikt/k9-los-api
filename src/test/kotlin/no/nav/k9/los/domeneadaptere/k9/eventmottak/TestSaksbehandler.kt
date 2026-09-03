package no.nav.k9.los.domeneadaptere.k9.eventmottak

import io.mockk.mockk
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.saksbehandleradmin.OpprettSaksbehandler
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.TestSaksbehandlerRepository
import org.koin.test.KoinTest
import org.koin.test.get
import javax.sql.DataSource

class TestSaksbehandler : KoinTest {

    val datasource = get<DataSource>()
    val pepClient = mockk<IPepClient>(relaxed = true)
    val repo = get<TestSaksbehandlerRepository>()

    companion object {
        val SARA = Saksbehandler(
            id = 1,
            navident = "Z123456",
            navn = "Sara Saksbehandler",
            epost = "sara.saksbehandler@nav.no",
            enhet = "2830 NAV DRIFT",
            områder = listOf(Områder.K9),
            kode6 = false
        )

        val BIRGER_BESLUTTER = Saksbehandler(
            id = 2,
            navident = "Z654321",
            navn = "Birger Beslutter",
            epost = "birger.beslutter@nav.no",
            enhet = "2830 NAV DRIFT",
            områder = listOf(Områder.K9),
            kode6 = false
        )

        val KJERSTI_SKJERMET = Saksbehandler(
            id = 3,
            navident = "Z999999",
            navn = "Kjersti Skjermet",
            epost = "kjersti.skjermet@nav.no",
            enhet = "SKJERMET",
            områder = listOf(Områder.K9),
            kode6 = true
        )
    }

    fun init() {
        repo.opprettSaksbehandler(SARA.tilOpprettSaksbehandler(), Områder.K9, skjermet = false)
        repo.opprettSaksbehandler(BIRGER_BESLUTTER.tilOpprettSaksbehandler(), Områder.K9, skjermet = false)
        leggTilSkjermet()
    }

    private fun leggTilSkjermet() {
        repo.opprettSaksbehandler(KJERSTI_SKJERMET.tilOpprettSaksbehandler(), Områder.K9, skjermet = true)
    }

    private fun Saksbehandler.tilOpprettSaksbehandler() = OpprettSaksbehandler(
        navident = navident,
        navn = navn,
        epost = epost,
        enhet = enhet
    )
}
