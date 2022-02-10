package no.nav.k9.fagsystem.k9sak

import no.nav.k9.domene.modell.Aksjonspunkter
import java.util.*
import javax.sql.DataSource

class K9SakRepository(
    val dataSource: DataSource
) {
    fun hentFagsystemData(uuid: UUID): FagsystemBehandlingData {
        return FagsystemBehandlingData(uuid, null, null, null, Aksjonspunkter(emptyMap()))
    }

    fun hentSaksbehandlingForAlle(uuid: Collection<String>): Map<String, FagsystemBehandlingData> {
        return emptyMap()
    }
}