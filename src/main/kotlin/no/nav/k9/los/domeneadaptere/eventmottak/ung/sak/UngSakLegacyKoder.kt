package no.nav.k9.los.domeneadaptere.eventmottak.ung.sak

import com.fasterxml.jackson.databind.util.StdConverter
import no.nav.ung.kodeverk.behandling.BehandlingStatus
import no.nav.ung.kodeverk.behandling.BehandlingStegType

object UngSakLegacyKoder {
    private val behandlingStatus = mapOf(
        "LOKALKONTOR_BESLUTTER_VILKÅR" to BehandlingStatus.NAV_KONTOR_BESLUTTER_VILKÅR.kode,
    )

    private val behandlingSteg = mapOf(
        "LOKALKONTOR_FORESLÅ_VILKÅR_STEG" to BehandlingStegType.NAV_KONTOR_FORESLÅ_VILKÅR.kode,
        "LOKALKONTOR_BESLUTTE_VILKÅR_STEG" to BehandlingStegType.NAV_KONTOR_BESLUTTER_VILKÅR.kode,
    )

    fun behandlingStatus(kode: String): String = behandlingStatus[kode] ?: kode
    fun behandlingSteg(kode: String): String = behandlingSteg[kode] ?: kode

    class BehandlingStatusConverter : StdConverter<String, String>() {
        override fun convert(value: String): String = behandlingStatus(value)
    }

    class BehandlingStegConverter : StdConverter<String, String>() {
        override fun convert(value: String): String = behandlingSteg(value)
    }
}
