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
    fun erUtledet(): Boolean {
        return feltutleder != null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Oppgavefelt

        if (id != other.id) return false
        if (feltDefinisjon != other.feltDefinisjon) return false
        if (visPåOppgave != other.visPåOppgave) return false
        if (påkrevd != other.påkrevd) return false
        if (defaultverdi != other.defaultverdi) return false
        if (feltutleder != other.feltutleder) return false

        return true
    }

    override fun toString(): String {
        return "Oppgavefelt(feltDefinisjon=$feltDefinisjon, visPåOppgave=$visPåOppgave, påkrevd=$påkrevd, defaultverdi=$defaultverdi, feltutleder=$feltutleder)"
    }
}
