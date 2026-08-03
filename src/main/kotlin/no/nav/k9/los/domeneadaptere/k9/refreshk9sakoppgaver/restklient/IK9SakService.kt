package no.nav.k9.los.domeneadaptere.k9.refreshk9sakoppgaver.restklient

import java.util.UUID

interface IK9SakService {
    suspend fun refreshBehandlinger(behandlingUuid: Collection<UUID>)
}