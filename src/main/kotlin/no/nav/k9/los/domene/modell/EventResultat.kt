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
        val koderSomLukkerOppgave = EnumSet.of(LUKK_OPPGAVE, LUKK_OPPGAVE_VENT, LUKK_OPPGAVE_MANUELT_VENT)
    }

    fun lukkerOppgave(): Boolean {
        return koderSomLukkerOppgave.contains(this)
    }

    fun beslutterOppgave(): Boolean {
        return this == OPPRETT_BESLUTTER_OPPGAVE
    }
}