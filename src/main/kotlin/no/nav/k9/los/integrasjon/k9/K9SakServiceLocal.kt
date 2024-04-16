package no.nav.k9.los.integrasjon.k9

import no.nav.k9.sak.kontrakt.behandling.BehandlingIdListe
import java.util.UUID

open class K9SakServiceLocal : IK9SakService {

    override suspend fun refreshBehandlinger(behandlingIder: Collection<UUID>) {

    }
}
