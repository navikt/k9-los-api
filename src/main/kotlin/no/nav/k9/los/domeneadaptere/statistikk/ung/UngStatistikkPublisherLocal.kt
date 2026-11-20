package no.nav.k9.los.domeneadaptere.statistikk.ung

object UngStatistikkPublisherLocal : UngStatistikkPublisher {
    override fun publiserAsynkront(behandling: UngBehandling) {}
    override fun flushOgValider() {}
}
