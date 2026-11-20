package no.nav.k9.los.domeneadaptere.statistikk.k9

interface K9StatistikkPublisher {
    fun publiserAsynkront(sak: Sak, k9Behandling: K9Behandling)
    fun flushOgValider()
}
