package no.nav.k9.los.fagsystem.k9sak

import io.prometheus.client.Histogram

class Metrics {

    companion object {
        val k9sakHendelseMetrikkAksjonspunkt = Histogram.build()
            .name("los-k9sak-hendelse-aksjonspunkt-v2")
            .help("Tidsforbruk k9sak hendelse (aksjonspunkt)")
            .register()

        val k9sakHendelseMetrikkBehandlingOpprettet = Histogram.build()
            .name("los-k9sak-hendelse-behandling-opprettet-v2")
            .help("Tidsforbruk k9sak hendelse (behandling opprettet)")
            .register()

        val k9sakHendelseMetrikkBehandlingAvsluttet = Histogram.build()
            .name("los-k9sak-hendelse-behandling-avsluttet-v2")
            .help("Tidsforbruk k9sak hendelse (behandling avsluttet)")
            .register()
    }
}