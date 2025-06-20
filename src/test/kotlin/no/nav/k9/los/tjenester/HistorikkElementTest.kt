package no.nav.k9.los.tjenester

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.FerdigstiltBehandling
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.VelgbartHistorikkfelt
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.feltSelector
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import org.junit.jupiter.api.Test
import java.time.LocalDate

class HistorikkElementTest {
    val DAG1 = LocalDate.of(2021, 1,1)
    val DAG2 = LocalDate.of(2021, 1,2)
    val DAG3 = LocalDate.of(2021, 1,3)

    val oppgaver = listOf(
        FerdigstiltBehandling(DAG1, FagsakYtelseType.PLEIEPENGER_SYKT_BARN.kode, BehandlingType.FORSTEGANGSSOKNAD.kode, behandlendeEnhet = "4407", ),
        FerdigstiltBehandling(DAG1, FagsakYtelseType.OMSORGSPENGER.kode, BehandlingType.FORSTEGANGSSOKNAD.kode, behandlendeEnhet = "4404"),

        FerdigstiltBehandling(DAG2, FagsakYtelseType.PLEIEPENGER_SYKT_BARN.kode, BehandlingType.REVURDERING.kode, behandlendeEnhet = "4407"),

        FerdigstiltBehandling(DAG3, FagsakYtelseType.PLEIEPENGER_SYKT_BARN.kode, BehandlingType.FORSTEGANGSSOKNAD.kode, behandlendeEnhet = "4407"),
        FerdigstiltBehandling(DAG3, FagsakYtelseType.PLEIEPENGER_SYKT_BARN.kode, BehandlingType.REVURDERING.kode, behandlendeEnhet = "4405"),
        FerdigstiltBehandling(DAG3, FagsakYtelseType.OMSORGSPENGER.kode, BehandlingType.FORSTEGANGSSOKNAD.kode, behandlendeEnhet = "4404"),
        FerdigstiltBehandling(DAG3, FagsakYtelseType.OMSORGSPENGER.kode, BehandlingType.REVURDERING.kode, behandlendeEnhet = "4404")
    )

    @Test
    fun `grupper kun paa dato`() {
        val resultat = oppgaver.feltSelector(VelgbartHistorikkfelt.DATO)
        assertThat(resultat).hasSize(3)
        assertThat(resultat[0].antall).isEqualTo(2)
        assertThat(resultat[1].antall).isEqualTo(1)
        assertThat(resultat[2].antall).isEqualTo(4)
    }

    @Test
    fun `grupper paa dato og ytelse`() {
        val resultat = oppgaver.feltSelector(VelgbartHistorikkfelt.DATO, VelgbartHistorikkfelt.YTELSETYPE)

        assertThat(resultat).hasSize(5)
        assertThat(resultat[0].antall).isEqualTo(1)
        assertThat(resultat[1].antall).isEqualTo(1)

        assertThat(resultat[2].antall).isEqualTo(1)

        assertThat(resultat[3].antall).isEqualTo(2)
        assertThat(resultat[4].antall).isEqualTo(2)
    }

    @Test
    fun `grupper paa dato, ytelse og enhet`() {
        val resultat = oppgaver.feltSelector(
            VelgbartHistorikkfelt.DATO,
            VelgbartHistorikkfelt.YTELSETYPE,
            VelgbartHistorikkfelt.ENHET
        )

        assertThat(resultat).hasSize(6)
        assertThat(resultat[0].antall).isEqualTo(1)
        assertThat(resultat[1].antall).isEqualTo(1)

        assertThat(resultat[2].antall).isEqualTo(1)

        assertThat(resultat[3].antall).isEqualTo(1)
        assertThat(resultat[4].antall).isEqualTo(1)
        assertThat(resultat[5].antall).isEqualTo(2)
    }

    @Test
    fun `serialiser til forventet format i frontend`() {
        val resultat = listOf(
            FerdigstiltBehandling(
                DAG3,
                FagsakYtelseType.PLEIEPENGER_SYKT_BARN.kode,
                BehandlingType.FORSTEGANGSSOKNAD.kode,
                behandlendeEnhet = "4407"
            )
        ).feltSelector(VelgbartHistorikkfelt.DATO, VelgbartHistorikkfelt.YTELSETYPE, VelgbartHistorikkfelt.ENHET)
        val result = LosObjectMapper.instance.writeValueAsString(resultat)
        println(result)
        assertThat(result).contains(
            """dato":"2021-01-03""",
            """behandlendeEnhet":"4407""",
            """antall":1"""
        )
    }
}