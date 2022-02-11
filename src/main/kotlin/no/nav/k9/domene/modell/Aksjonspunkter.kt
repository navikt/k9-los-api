package no.nav.k9.domene.modell

import no.nav.k9.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.integrasjon.kafka.dto.PunsjEventDto
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.kodeverk.behandling.aksjonspunkt.Venteårsak
import no.nav.k9.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto
import java.time.LocalDateTime
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus as AksjonspunktStatusK9

data class Aksjonspunkter(
    // ikke bruk denne direkte gå via hentAlle eller hentAktive
    val liste: Map<String, String>,
    val apTilstander: List<AksjonspunktTilstand> = emptyList()
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

data class AksjonspunktTilstand(
    val aksjonspunktKode: String,
    val status: AksjonspunktStatus,
    val venteårsak: String? = null,
    val frist: LocalDateTime? = null
)

internal fun BehandlingProsessEventDto.tilAksjonspunkter(): Aksjonspunkter {
    return Aksjonspunkter(
        this.aksjonspunktKoderMedStatusListe,
        this.aksjonspunktTilstander.map { it.tilModell() })
}

internal fun BehandlingProsessEventDto.tilAktiveAksjonspunkter(): Aksjonspunkter {
    return Aksjonspunkter(
        this.aksjonspunktKoderMedStatusListe.filter { it.value == AksjonspunktStatus.OPPRETTET.kode },
        this.aksjonspunktTilstander.filter { it.status == AksjonspunktStatusK9.OPPRETTET }.map { it.tilModell() })
}

private fun AksjonspunktTilstandDto.tilModell() = AksjonspunktTilstand(
    this.aksjonspunktKode,
    AksjonspunktStatus.fraKode(this.status.kode),
    if (this.venteårsak == Venteårsak.UDEFINERT) null else this.venteårsak.kode,
    this.fristTid
)

internal fun PunsjEventDto.tilAksjonspunkter(): Aksjonspunkter {
    return Aksjonspunkter(
        this.aksjonspunktKoderMedStatusListe,
        this.aksjonspunktKoderMedStatusListe.map { AksjonspunktTilstand(it.key, AksjonspunktStatus.fraKode(it.value)) })
}

internal fun PunsjEventDto.tilAktiveAksjonspunkter(): Aksjonspunkter {
    return this.aksjonspunktKoderMedStatusListe
        .filter { it.value == AksjonspunktStatus.OPPRETTET.kode }
        .let {
            Aksjonspunkter(
                it,
                it.map { aktiv -> AksjonspunktTilstand(aktiv.key, AksjonspunktStatus.fraKode(aktiv.value)) })
        }
}

