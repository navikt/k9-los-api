package no.nav.k9.los.domeneadaptere.statistikk

data class StatistikkPublishere(
    val k9StatistikkSak: StatistikkPublisher,
    val k9StatistikkBehandling: StatistikkPublisher,
    val avpStatistikkBehandling: StatistikkPublisher,
    val ungStatistikkBehandling: StatistikkPublisher,
)
