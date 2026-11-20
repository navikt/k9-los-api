package no.nav.k9.los.domeneadaptere.statistikk.ung

interface UngStatistikkPublisher {
    fun publiserAsynkront(behandling: UngBehandling)
    fun flushOgValider()
}
