package no.nav.k9.los.domeneadaptere.statistikk

data class StatistikkPublishere(
    val sakK9: StatistikkPublisher,
    val behandlingK9: StatistikkPublisher,
    val behandlingAvp: StatistikkPublisher,
    val behandlingUng: StatistikkPublisher,
)
