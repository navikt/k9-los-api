package no.nav.k9.los.integrasjon.k9

import no.nav.k9.sak.kontrakt.behandling.BehandlingIdListe
import java.util.UUID

interface IK9SakService {
    suspend fun refreshBehandlinger(behandlingUuid: Collection<UUID>)
}