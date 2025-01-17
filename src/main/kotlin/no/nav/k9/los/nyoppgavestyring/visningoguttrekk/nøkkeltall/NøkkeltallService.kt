package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall

import no.nav.k9.los.domene.modell.BehandlingType
import no.nav.k9.los.domene.modell.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator
import java.time.LocalDate

class NøkkeltallService(
    val queryService: OppgaveQueryService
) {
    fun dagensTall() : List<DagensTallDto> {
        val ytelser = listOf(
            FagsakYtelseType.OMSORGSPENGER,
            FagsakYtelseType.OMSORGSDAGER,
            FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
            FagsakYtelseType.PPN
        )
        //PUNSJ
        val behandlingstyper = listOf(BehandlingType.FORSTEGANGSSOKNAD, BehandlingType.REVURDERING)

        val resultatliste = mutableListOf<DagensTallDto>()

        for (ytelseType in ytelser) {
            for (behandlingType in behandlingstyper) {
                val inngangIDag = hentTall(
                    behandlingType, ytelseType, EksternFeltverdiOperator.EQUALS, LocalDate.now(), Datotype.MOTTATTDATO
                )
                val ferdigstilteIDag = hentTall(
                    behandlingType, ytelseType, EksternFeltverdiOperator.EQUALS, LocalDate.now(), Datotype.VEDTAKSDATO
                )
                val inngangSiste7Dager = hentTall(
                    behandlingType,
                    ytelseType,
                    EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS,
                    LocalDate.now().minusDays(7),
                    Datotype.VEDTAKSDATO
                )
                val ferdigstilteSiste7Dager = hentTall(
                    behandlingType,
                    ytelseType,
                    EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS,
                    LocalDate.now().minusDays(7),
                    Datotype.VEDTAKSDATO
                )

                resultatliste.add(
                    DagensTallDto(
                        dagensTallType = DagensTallType.fraFagsakYtelseType(ytelseType),
                        behandlingType = behandlingType,
                        nyeIDag = inngangIDag,
                        ferdigstilteIDag = ferdigstilteIDag,
                        nyeSiste7Dager = inngangSiste7Dager,
                        ferdigstilteSiste7Dager = ferdigstilteSiste7Dager
                    )
                )
            }
        }

        val punsjInngangIDag = hentTallPunsj(EksternFeltverdiOperator.EQUALS, LocalDate.now(), Datotype.MOTTATTDATO)
        val punsjInngangSiste7Dager = hentTallPunsj(EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS, LocalDate.now().minusDays(7), Datotype.MOTTATTDATO)
        val punsjFerdigstiltIDag = hentTallPunsj(EksternFeltverdiOperator.EQUALS, LocalDate.now(), Datotype.SISTENDRET)
        val punsjFerdigstiltSiste7Dager = hentTallPunsj(EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS, LocalDate.now().minusDays(7), Datotype.SISTENDRET)
        
        resultatliste.add(
            DagensTallDto(
                dagensTallType = DagensTallType.PUNSJ,
                behandlingType = null,
                nyeIDag = punsjInngangIDag,
                ferdigstilteIDag = punsjFerdigstiltIDag,
                nyeSiste7Dager = punsjInngangSiste7Dager,
                ferdigstilteSiste7Dager = punsjFerdigstiltSiste7Dager
            )
        )

        return resultatliste
    }

    private fun hentTall(
        behandlingType: BehandlingType,
        fagsakYtelseType: FagsakYtelseType,
        operator: EksternFeltverdiOperator,
        dato: LocalDate,
        datotype: Datotype
    ): Long {
        return queryService.queryForAntall(
            QueryRequest(
                oppgaveQuery = OppgaveQuery(
                    filtere = listOf(
                        FeltverdiOppgavefilter(
                            "K9", "ytelsestype", EksternFeltverdiOperator.EQUALS.kode, listOf(fagsakYtelseType.kode)
                        ), FeltverdiOppgavefilter(
                            "K9",
                            "behandlingTypekode",
                            EksternFeltverdiOperator.EQUALS.kode,
                            listOf(behandlingType.kode)
                        ), FeltverdiOppgavefilter("K9", datotype.kode, operator.kode, listOf(dato.toString()))
                    )
                ),
                fraAktiv = false,
            )
        )
    }

    private fun hentTallPunsj(
        operator: EksternFeltverdiOperator,
        dato: LocalDate,
        datotype: Datotype
    ): Long {
        return queryService.queryForAntall(
            QueryRequest(
                oppgaveQuery = OppgaveQuery(
                    filtere = listOf(
                        FeltverdiOppgavefilter(
                            "K9", "oppgavetype", EksternFeltverdiOperator.EQUALS.kode, listOf("k9punsj")
                        ), FeltverdiOppgavefilter("K9", datotype.kode, operator.kode, listOf(dato.toString()))
                    )
                ),
                fraAktiv = false,
            )
        )
    }

    private enum class Datotype(val kode: String) {
        VEDTAKSDATO("vedtaksdato"), MOTTATTDATO("mottattDato"), SISTENDRET("sistEndret")
    }
}