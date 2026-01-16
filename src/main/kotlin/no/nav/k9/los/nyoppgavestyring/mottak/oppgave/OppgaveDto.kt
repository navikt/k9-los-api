package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import java.time.LocalDateTime

sealed class NyOppgaveVersjonInnsending(
    open val dto: OppgaveDto
)

data class NyOppgaveversjon(
    override val dto: OppgaveDto
): NyOppgaveVersjonInnsending(dto)

data class VaskOppgaveversjon(
    override val dto: OppgaveDto,
    val eventNummer: Int
): NyOppgaveVersjonInnsending(dto)

data class OppgaveDto(
    val eksternId: String,
    val eksternVersjon: String,
    val område: String,
    val kildeområde: String,
    val type: String,
    val status: String,
    val endretTidspunkt: LocalDateTime,
    val reservasjonsnøkkel: String,
    val feltverdier: List<OppgaveFeltverdiDto>
) {

    constructor(oppgaveV3: OppgaveV3) : this(
        eksternId = oppgaveV3.eksternId,
        eksternVersjon = oppgaveV3.eksternVersjon,
        område = oppgaveV3.oppgavetype.område.eksternId,
        kildeområde = oppgaveV3.kildeområde,
        type = oppgaveV3.oppgavetype.eksternId,
        status = oppgaveV3.status.kode,
        endretTidspunkt = oppgaveV3.endretTidspunkt,
        reservasjonsnøkkel = oppgaveV3.reservasjonsnøkkel,
        feltverdier = oppgaveV3.felter.map { felt ->
            OppgaveFeltverdiDto(
                nøkkel = felt.oppgavefelt.feltDefinisjon.eksternId,
                verdi = felt.verdi
            )
        }
    )

    constructor(oppgaveDto: OppgaveDto, feltverdier: List<OppgaveFeltverdiDto>) : this(
        eksternId = oppgaveDto.eksternId,
        eksternVersjon = oppgaveDto.eksternVersjon,
        område = oppgaveDto.område,
        kildeområde = oppgaveDto.kildeområde,
        type = oppgaveDto.type,
        status = oppgaveDto.status,
        endretTidspunkt = oppgaveDto.endretTidspunkt,
        reservasjonsnøkkel = oppgaveDto.reservasjonsnøkkel,
        feltverdier = feltverdier,
    )

    fun leggTilFeltverdi(oppgaveFeltverdi: OppgaveFeltverdiDto): OppgaveDto {
        return OppgaveDto(this, this.feltverdier.plus(oppgaveFeltverdi))
    }

    fun erstattFeltverdi(oppgaveFeltverdi: OppgaveFeltverdiDto): OppgaveDto {
        return OppgaveDto(this, this.feltverdier.filterNot { it.nøkkel == oppgaveFeltverdi.nøkkel }.plus(oppgaveFeltverdi))
    }
}
