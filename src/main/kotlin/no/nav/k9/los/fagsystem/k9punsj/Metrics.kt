package no.nav.k9.los.fagsystem.k9punsj

import io.prometheus.client.Histogram

class Metrics {

    companion object {

        val k9punsjHendelseMetrikkBehandlingOpprettet = Histogram.build()
            .name("los-k9punsj-hendelse-behandling-opprettet")
            .help("Tidsforbruk k9punsj hendelse (behandling opprettet)")
            .register()

        val k9punsjHendelseMetrikkBehandlingAvbrutt = Histogram.build()
            .name("los-k9punsj-hendelse-behandling-avbrutt")
            .help("Tidsforbruk k9punsj hendelse (behandling avbrutt)")
            .register()

        val k9punsjHendelseMetrikkBehandlingFerdigstilt = Histogram.build()
            .name("los-k9punsj-hendelse-behandling-ferdigstilt")
            .help("Tidsforbruk k9punsj hendelse (behandling ferdigstilt)")
            .register()
    }
}