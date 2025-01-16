package no.nav.k9.los.nyoppgavestyring.query.mapping

import assertk.assertThat
import assertk.assertions.*
import no.nav.k9.los.nyoppgavestyring.FeltType
import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import org.junit.jupiter.api.Test

class OppgavefilterUtenBetingelserFjernerTest {
    @Test
    fun `Skal ikke fjerne filtre med verdi`() {
        val resultat = OppgavefilterUtenBetingelserFjerner.fjern(
            listOf(
                FeltverdiOppgavefilter(
                    "K9",
                    FeltType.BEHANDLINGUUID.eksternId,
                    "IN",
                    listOf("", null)
                )
            )
        )
        assertThat(resultat).isNotEmpty()
    }

    @Test
    fun `Skal fjerne filtre uten verdi i liste`() {
        val resultat = OppgavefilterUtenBetingelserFjerner.fjern(
            listOf(
                FeltverdiOppgavefilter(
                    "K9",
                    FeltType.BEHANDLINGUUID.eksternId,
                    "IN",
                    listOf()
                )
            )
        )
        assertThat(resultat).isEmpty()
    }

    @Test
    fun `Skal ikke fjerne filtre med kun null verdi`() {
        val resultat = OppgavefilterUtenBetingelserFjerner.fjern(
            listOf(
                FeltverdiOppgavefilter(
                    "K9",
                    FeltType.BEHANDLINGUUID.eksternId,
                    "IN",
                    listOf(null)
                )
            )
        )
        assertThat(resultat).isNotEmpty()
    }

    @Test
    fun `Skal ikke fjerne combinefilter med verdi`() {
        val resultat = OppgavefilterUtenBetingelserFjerner.fjern(
            listOf(
                CombineOppgavefilter(
                    "AND",
                    listOf(FeltverdiOppgavefilter("K9", FeltType.BEHANDLINGUUID.eksternId, "EQUALS", listOf("")))
                )
            )
        )
        assertThat(resultat).isNotEmpty()
    }

    @Test
    fun `Vanskeligste case - skal fjerne rekursivt`() {
        val resultat = OppgavefilterUtenBetingelserFjerner.fjern(
            listOf(
                FeltverdiOppgavefilter("K9", FeltType.BEHANDLINGUUID.eksternId, "EQUALS", listOf()),
                FeltverdiOppgavefilter(
                    "K9",
                    FeltType.YTELSE_TYPE.eksternId,
                    "EQUALS",
                    listOf("ytelsestype")
                ),
                CombineOppgavefilter(
                    "AND",
                    listOf(
                        CombineOppgavefilter(
                            "AND",
                            listOf(FeltverdiOppgavefilter("K9", FeltType.BEHANDLINGUUID.eksternId, "EQUALS", listOf()))
                        )
                    )
                )
            )
        )
        assertThat(resultat).hasSize(1)
        assertThat(resultat[0])
            .isInstanceOf(FeltverdiOppgavefilter::class.java)
            .transform { it.kode }.isEqualTo(FeltType.YTELSE_TYPE.eksternId)
    }
}