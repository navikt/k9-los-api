package no.nav.k9.los.forvaltning

import no.nav.k9.los.domeneadaptere.statistikk.Aktør
import no.nav.k9.los.domeneadaptere.statistikk.SensitiveFieldCensor
import no.nav.k9.los.domeneadaptere.statistikk.avp.AvpBehandling
import no.nav.k9.los.domeneadaptere.statistikk.k9.K9Behandling
import no.nav.k9.los.domeneadaptere.statistikk.k9.Sak
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SensitiveFieldCensorTest {

    @Test
    fun `censors only annotated fields and preserves nested actor shape`() {
        val behandling = AvpBehandling(
            behandlingId = "behandling-1",
            søkersAktørId = Aktør(123L, "Søker", "Søker"),
            saksnummer = "sak-1",
            beslutter = "Z123",
            saksbehandler = "Z456",
            ansvarligEnhetKode = "1234",
        )

        val censored = SensitiveFieldCensor.censor(behandling)

        assertEquals(Aktør(-5L, "-5", "-5"), censored.søkersAktørId)
        assertEquals("-5", censored.beslutter)
        assertEquals("-5", censored.saksbehandler)
        assertEquals("-5", censored.ansvarligEnhetKode)
        assertEquals("sak-1", censored.saksnummer)
        assertEquals("behandling-1", censored.behandlingId)
    }

    @Test
    fun `censors annotated numeric and nested list fields`() {
        val sak = Sak(
            saksnummer = "sak-1",
            aktorId = 123L,
            aktorer = listOf(Aktør(123L, "Søker", "Søker")),
        )

        val censored = SensitiveFieldCensor.censor(sak)

        assertEquals(-5L, censored.aktorId)
        assertEquals(listOf(Aktør(-5L, "-5", "-5")), censored.aktorer)
        assertEquals("sak-1", censored.saksnummer)
    }

    @Test
    fun `censors nullable annotated properties`() {
        val behandling = K9Behandling(
            behandlingId = "behandling-1",
            beslutter = null,
            saksbehandler = "Z123",
            ansvarligEnhetKode = "1234",
        )

        val censored = SensitiveFieldCensor.censor(behandling)

        assertEquals("-5", censored.beslutter)
        assertEquals("-5", censored.saksbehandler)
        assertEquals("-5", censored.ansvarligEnhetKode)
        assertEquals("behandling-1", censored.behandlingId)
    }
}
