package no.nav.k9.los.tjenester.avdelingsleder.oppgaveko

import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType

data class BehandlingsTypeDto(
    val id: String,
    val behandlingsTyper: MutableList<TypeMedStatus>,
) {
    data class TypeMedStatus(
        val behandlingType: BehandlingType,
        val checked: Boolean
    )
}
