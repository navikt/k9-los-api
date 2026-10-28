package no.nav.k9.los.infrastruktur.abac.tilganger

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class InnloggetAnsattK9V2DtoTest {
    private val utenRettigheter = InnloggetAnsattK9V2Dto(
        kanVeilede = false,
        kanBehandleKode6 = false,
        k9SaksbehandlerTilgang = SaksbehandlerTilgangDto(false),
        kanOppgavestyre = false,
        kanDrifte = false,
    )
    private val ingenTilganger = Tilganger(
        basis = false,
        kode6 = false,
        oppgavestyring = false,
        reservering = false,
        drift = false,
    )

    @Test
    fun `gir ingen tilganger uten rettigheter`() {
        utenRettigheter.tilTilganger() shouldBe ingenTilganger
    }

    @Test
    fun `veileder får basis men ikke reservering`() {
        utenRettigheter.copy(kanVeilede = true).tilTilganger() shouldBe ingenTilganger.copy(basis = true)
    }

    @Test
    fun `saksbehandler får basis og reservering`() {
        utenRettigheter.copy(k9SaksbehandlerTilgang = SaksbehandlerTilgangDto(true)).tilTilganger() shouldBe
                ingenTilganger.copy(basis = true, reservering = true)
    }

    @Test
    fun `veileder som også er saksbehandler får basis og reservering`() {
        utenRettigheter.copy(
            kanVeilede = true,
            k9SaksbehandlerTilgang = SaksbehandlerTilgangDto(true),
        ).tilTilganger() shouldBe ingenTilganger.copy(basis = true, reservering = true)
    }

    @Test
    fun `mapper kode6 oppgavestyring og drift uavhengig av hverandre`() {
        utenRettigheter.copy(kanBehandleKode6 = true).tilTilganger() shouldBe ingenTilganger.copy(kode6 = true)
        utenRettigheter.copy(kanOppgavestyre = true).tilTilganger() shouldBe ingenTilganger.copy(oppgavestyring = true)
        utenRettigheter.copy(kanDrifte = true).tilTilganger() shouldBe ingenTilganger.copy(drift = true)
    }
}
