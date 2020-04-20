package no.nav.k9.tjenester.avdelingsleder.oppgaveko

import no.nav.k9.domene.modell.BehandlingType

data class BehandlingsTypeDto(
    val oppgavekoId: OppgavekøIdDto,
    val behandlingType: BehandlingType,
    val markert: Boolean
)
