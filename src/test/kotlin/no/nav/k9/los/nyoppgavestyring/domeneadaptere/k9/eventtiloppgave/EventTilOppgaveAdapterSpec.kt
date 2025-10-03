package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave

import io.kotest.core.spec.style.FreeSpec
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager

import org.koin.test.KoinTest
import org.koin.test.inject

class EventTilOppgaveAdapterSpec : KoinTest, FreeSpec() {
    val transactionalManager by inject<TransactionalManager>()

    init {
        "test" - {
            "test2" {
                //val transactionalManager = getKoin().get<TransactionalManager>()
                transactionalManager.transaction { print("hello world") }
            }
        }
    }
}