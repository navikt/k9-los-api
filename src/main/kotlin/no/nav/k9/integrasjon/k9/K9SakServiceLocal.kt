package no.nav.k9.integrasjon.k9

import no.nav.k9.sak.kontrakt.behandling.BehandlingIdListe

open class K9SakServiceLocal constructor(
) : IK9SakService {
    override suspend fun refreshBehandlinger(behandlingIdList: BehandlingIdListe) {

    }
}