package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.refreshk9sakoppgaver

import java.util.UUID

interface IK9SakService {
    suspend fun refreshBehandlinger(behandlingUuid: Collection<UUID>)
}