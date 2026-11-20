package no.nav.k9.los.domeneadaptere.statistikk.avp

interface AvpStatistikkPublisher {
    fun publiserAsynkront(behandling: AvpBehandling)
    fun flushOgValider()
}
