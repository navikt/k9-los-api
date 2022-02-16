package no.nav.k9.fagsystem.k9sak

import no.nav.k9.domene.modell.Aksjonspunkter
import java.util.*
import javax.sql.DataSource

class K9SakRepository(
    val dataSource: DataSource
) {
    fun hentFagsystemData(uuid: UUID): BehandlingdataK9Sak {
        return BehandlingdataK9Sak(uuid, null, null, null, Aksjonspunkter(emptyMap()))
    }

    fun hentSaksbehandlingForAlle(uuid: Collection<String>): Map<String, BehandlingdataK9Sak> {
        return emptyMap()
    }
}