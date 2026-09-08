package no.nav.k9.los.oppgavemottak

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
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
    val type: OppgaveDtoType,
    val status: String,
    val endretTidspunkt: LocalDateTime,
    val reservasjonsnøkkel: String,
    val feltverdier: List<OppgaveFeltverdiDto>
) {
    val område: Områder get() = type.område
    val kildeområde: Områder get() = område


    constructor(oppgaveV3: OppgaveV3) : this(
        eksternId = oppgaveV3.eksternId,
        eksternVersjon = oppgaveV3.eksternVersjon,
        type = OppgaveDtoType.fraEksternId(oppgaveV3.oppgavetype.område.eksternId, oppgaveV3.oppgavetype.eksternId),
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
