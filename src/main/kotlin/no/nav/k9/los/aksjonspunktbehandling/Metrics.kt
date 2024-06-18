package no.nav.k9.los.aksjonspunktbehandling

import io.prometheus.client.Histogram

class Metrics {

    companion object {
        val k9sakHendelseMetrikkVaskeevent = Histogram.build()
            .name("los_k9sak_hendelse_vaskeevent_v1")
            .help("Tidsforbruk k9sak hendelse (vaskehendelse)")
            .register()

        val k9sakHendelseMetrikkSkip = Histogram.build()
            .name("los_k9sak_hendelse_skip_v1")
            .help("Tidsforbruk k9sak hendelse (skipped)")
            .register()

        val k9sakHendelseMetrikkGjennomført = Histogram.build()
            .name("los_k9sak_hendelse_gjennomfoert_v1")
            .help("Tidsforbruk k9sak hendelse (gjennomført)")
            .register()

        val k9punsjHendelseMetrikkGjennomført = Histogram.build()
            .name("los_k9punsj_hendelse_gjennomfoert_v1")
            .help("Tidsforbruk k9punsj hendelse (gjennomført)")
            .register()

        val k9tilbakeHendelseMetrikkGjennomført = Histogram.build()
            .name("los_k9tilbake_hendelse_gjennomfoert_v1")
            .help("Tidsforbruk k9tilbake hendelse (gjennomført)")
            .register()

        val k9klageHendelseMetrikkGjennomført = Histogram.build()
            .name("los_k9klage_hendelse_gjennomfoert_v1")
            .help("Tidsforbruk k9klage hendelse (gjennomført)")
            .register()

        val k9klageHendelseMetrikkVaskeevent = Histogram.build()
            .name("los_k9klage_hendelse_vaskeevent_v1")
            .help("Tidsforbruk k9klage hendelse (vaskehendelse)")
            .register()
    }
}