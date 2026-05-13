package no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype

import no.nav.k9.los.nyoppgavestyring.feltutlederforlagring.Feltutleder
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjon

class Oppgavefelt(
    val id : Long? = null,
    val feltDefinisjon: Feltdefinisjon,
    val visPåOppgave: Boolean,
    val påkrevd: Boolean,
    val defaultverdi: String?,
    val feltutleder: Feltutleder? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Oppgavefelt

        if (id != other.id) return false //FIXME? risikerer å få feil for like objekter når det ene er i databasn med id, og det andre ikke er i databaesn
        if (feltDefinisjon != other.feltDefinisjon) return false
        if (visPåOppgave != other.visPåOppgave) return false
        if (påkrevd != other.påkrevd) return false
        if (defaultverdi != other.defaultverdi) return false
        if (feltutleder != other.feltutleder) return false

        return true
    }

    override fun hashCode(): Int {
        var result = feltDefinisjon.hashCode()
        result = 31 * result + visPåOppgave.hashCode()
        return result
    }

    override fun toString(): String {
        return "Oppgavefelt(feltDefinisjon=$feltDefinisjon, visPåOppgave=$visPåOppgave, påkrevd=$påkrevd, defaultverdi=$defaultverdi, feltutleder=$feltutleder)"
    }
}
