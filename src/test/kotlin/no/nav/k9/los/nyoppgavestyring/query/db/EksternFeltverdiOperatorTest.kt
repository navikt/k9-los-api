package no.nav.k9.los.nyoppgavestyring.query.db

import assertk.assertThat
import assertk.assertions.containsAll
import org.junit.jupiter.api.Test

class EksternFeltverdiOperatorTest {

    @Test
    fun `Skal inneholde alle typene som finnes i den interne FeltVerdiOperator`() {
        val feltverdiOperatorBruktEksternt = EksternFeltverdiOperator.entries.map { it.name }
        val feltverdiOperatorBruktInternt = FeltverdiOperator.entries.map { it.name }
        assertThat(feltverdiOperatorBruktEksternt).containsAll(*feltverdiOperatorBruktInternt.toTypedArray())
    }
}