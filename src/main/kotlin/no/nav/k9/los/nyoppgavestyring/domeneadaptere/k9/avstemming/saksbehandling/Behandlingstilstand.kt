package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.saksbehandling

import com.fasterxml.jackson.annotation.JsonAlias
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import java.time.LocalDateTime

data class Behandlingstilstand(
    val saksnummer: String,
    @JsonAlias("behandlingUuid")
    val eksternId: String,
    val behandlingStatus: BehandlingStatus,
    val ytelseType: FagsakYtelseType,
    val ventefrist: LocalDateTime?,
    val harManueltAksjonspunkt: Boolean
) {
    fun harAutopunkt(): Boolean {
        return ventefrist != null
    }
}