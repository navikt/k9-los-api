package no.nav.k9.los.domene.modell

import java.util.*

enum class EventResultat {
    LUKK_OPPGAVE,
    LUKK_OPPGAVE_VENT,
    LUKK_OPPGAVE_MANUELT_VENT,
    GJENÅPNE_OPPGAVE,
    OPPRETT_BESLUTTER_OPPGAVE,
    OPPRETT_PAPIRSØKNAD_OPPGAVE,
    OPPRETT_OPPGAVE;

    companion object {
        val koderSomSetterPåVent = EnumSet.of(LUKK_OPPGAVE_VENT, LUKK_OPPGAVE_MANUELT_VENT)
        val koderSomLukkerOppgave = EnumSet.of(LUKK_OPPGAVE, LUKK_OPPGAVE_VENT, LUKK_OPPGAVE_MANUELT_VENT)
        val koderSomÅpnerOppgave = EnumSet.of(GJENÅPNE_OPPGAVE, OPPRETT_BESLUTTER_OPPGAVE, OPPRETT_PAPIRSØKNAD_OPPGAVE, OPPRETT_OPPGAVE)
    }

    fun lukkerOppgave(): Boolean {
        return koderSomLukkerOppgave.contains(this)
    }

    fun setterOppgavePåVent(): Boolean {
        return koderSomSetterPåVent.contains(this)
    }

    fun åpnerOppgave(): Boolean {
        return koderSomÅpnerOppgave.contains(this)
    }

    fun beslutterOppgave(): Boolean {
        return this == OPPRETT_BESLUTTER_OPPGAVE
    }
}