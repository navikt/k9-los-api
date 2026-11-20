package no.nav.k9.los.domeneadaptere.statistikk.k9

object K9StatistikkPublisherLocal : K9StatistikkPublisher {
    override fun publiserAsynkront(sak: Sak, k9Behandling: K9Behandling) {}
    override fun flushOgValider() {}
}
