package no.nav.k9.los.domeneadaptere.statistikk

interface StatistikkPublisher {
    /**
     * Legger meldingen i producer-bufferen uten å blokkere.
     * Kall [flushOgValider] periodisk for å sikre levering og oppdage feil.
     */
    fun publiserAsynkront(nøkkel: String, melding: Any)

    /**
     * Blokkerer til alle buffrede meldinger er sendt og bekreftet.
     * Kaster exception hvis noen av de asynkrone sendingene feilet.
     */
    fun flushOgValider()
}
