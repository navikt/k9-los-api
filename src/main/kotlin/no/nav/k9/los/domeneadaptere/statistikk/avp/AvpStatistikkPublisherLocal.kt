package no.nav.k9.los.domeneadaptere.statistikk.avp

object AvpStatistikkPublisherLocal : AvpStatistikkPublisher {
    override fun publiserAsynkront(behandling: AvpBehandling) {}
    override fun flushOgValider() {}
}
