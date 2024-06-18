package no.nav.k9.los.aksjonspunktbehandling

import io.prometheus.client.Histogram

class Metrics {

    companion object {
        val k9sakHendelseMetrikkVaskeevent = Histogram.build()
            .name("los-k9sak-hendelse-vaskeevent-v1")
            .help("Tidsforbruk k9sak hendelse (vaskehendelse)")
            .register()

        val k9sakHendelseMetrikkSkip = Histogram.build()
            .name("los-k9sak-hendelse-skip-v1")
            .help("Tidsforbruk k9sak hendelse (skipped)")
            .register()

        val k9sakHendelseMetrikkGjennomført = Histogram.build()
            .name("los-k9sak-hendelse-gjennomfoert-v1")
            .help("Tidsforbruk k9sak hendelse (gjennomført)")
            .register()

        val k9punsjHendelseMetrikkGjennomført = Histogram.build()
            .name("los-k9punsj-hendelse-gjennomfoert-v1")
            .help("Tidsforbruk k9punsj hendelse (gjennomført)")
            .register()

        val k9tilbakeHendelseMetrikkGjennomført = Histogram.build()
            .name("los-k9tilbake-hendelse-gjennomfoert-v1")
            .help("Tidsforbruk k9tilbake hendelse (gjennomført)")
            .register()

        val k9klageHendelseMetrikkGjennomført = Histogram.build()
            .name("los-k9klage-hendelse-gjennomfoert-v1")
            .help("Tidsforbruk k9klage hendelse (gjennomført)")
            .register()

        val k9klageHendelseMetrikkVaskeevent = Histogram.build()
            .name("los-k9klage-hendelse-vaskeevent-v1")
            .help("Tidsforbruk k9klage hendelse (vaskehendelse)")
            .register()
    }
}