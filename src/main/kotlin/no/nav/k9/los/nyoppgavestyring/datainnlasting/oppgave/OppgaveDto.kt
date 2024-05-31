package no.nav.k9.los.nyoppgavestyring.datainnlasting.oppgave

import java.time.LocalDateTime

data class OppgaveDto(
    val id: String,
    val versjon: String,
    val område: String,
    val kildeområde: String,
    val type: String,
    val status: String,
    val endretTidspunkt: LocalDateTime,
    val reservasjonsnøkkel: String,
    val feltverdier: List<OppgaveFeltverdiDto>
) {

    constructor(oppgaveV3: OppgaveV3) : this(
        id = oppgaveV3.eksternId,
        versjon = oppgaveV3.eksternVersjon,
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
        id = oppgaveDto.id,
        versjon = oppgaveDto.versjon,
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
