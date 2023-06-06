package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

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
