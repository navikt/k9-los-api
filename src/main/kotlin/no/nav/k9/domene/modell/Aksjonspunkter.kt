package no.nav.k9.domene.modell

import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon

data class Aksjonspunkter(
    // ikke bruk denne direkte gå via hentAlle eller hentAktive
    val liste: Map<String, String>
) {
    fun hentLengde(): Int {
        return liste.filter { entry -> entry.value == AKTIV }.size
    }
    fun hentAlle(): Map<String, String> {
        return liste
    }
    fun hentAktive(): Map<String, String> {
        return liste.filter { entry -> entry.value == AKTIV }
    }
    fun påVent(): Boolean {
        return AksjonspunktDefWrapper.påVent(this.liste)
    }

    fun erIngenAktive(): Boolean {
        return hentAktive().isEmpty()
    }

    fun tilBeslutter(): Boolean {
        return AksjonspunktDefWrapper.tilBeslutter(this.liste)
    }

    fun harAktivtAksjonspunkt(def: AksjonspunktDefinisjon): Boolean {
        return AksjonspunktDefWrapper.inneholderEtAktivtAksjonspunktMedKoden(this.liste, def)
    }

    fun alleAktiveAksjonspunktTaBortPunsj(): Aksjonspunkter {
        return Aksjonspunkter(
            liste.filter { entry -> entry.value == AKTIV }
                .filter { entry ->
                    !AksjonspunktDefWrapper.aksjonspunkterFraPunsj().map { it.kode }.contains(entry.key)
                }
        )
    }

    fun harInaktivtAksjonspunkt(def: AksjonspunktDefinisjon): Boolean {
        return AksjonspunktDefWrapper.inneholderEtInaktivtAksjonspunktMedKoden(this.liste, def)
    }

    fun harAtAvAktivtAksjonspunkt(vararg def: AksjonspunktDefinisjon): Boolean {
        return AksjonspunktDefWrapper.inneholderEtAvAktivtAksjonspunktMedKoden(this.liste, def.toList())
    }

    fun harEtAvInaktivtAksjonspunkt(vararg def: AksjonspunktDefinisjon): Boolean {
        return AksjonspunktDefWrapper.inneholderEtAvInaktivtAksjonspunkterMedKoder(this.liste, def.toList())
    }

    fun eventResultat(): EventResultat {
        if (erIngenAktive()) {
            return EventResultat.LUKK_OPPGAVE
        }

        if (påVent()) {
            return EventResultat.LUKK_OPPGAVE_VENT
        }

        if (tilBeslutter()) {
            return EventResultat.OPPRETT_BESLUTTER_OPPGAVE
        }

        return EventResultat.OPPRETT_OPPGAVE
    }

    companion object {
        private const val AKTIV = "OPPR"
    }
}

internal fun MutableMap<String, String>.tilAksjonspunkter() : Aksjonspunkter{
    return Aksjonspunkter(this)
}

internal fun MutableMap<String, String>.tilAktiveAksjonspunkter() : Aksjonspunkter{
    return Aksjonspunkter(this.filter { it.value == "OPPR" })
}

