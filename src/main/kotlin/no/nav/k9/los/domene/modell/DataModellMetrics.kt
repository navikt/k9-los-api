package no.nav.k9.los.domene.modell

import io.prometheus.client.Counter
import io.prometheus.client.Histogram
import no.nav.k9.los.domene.repository.ReservasjonRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakModell
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import java.time.Duration

private val oppgaveOpprettet = Counter.build()
    .name("oppgaveOpprettet_counter")
    .help("Teller for antall opprettede oppgaver")
    .register()

private val oppgaveAvsluttet = Counter.build()
    .name("oppgaveAvsluttet_counter")
    .help("Teller for antall avsluttede oppgaver")
    .register()

private val oppgaveAutomatisk = Counter.build()
    .name("oppgaveAutomatisk_counter")
    .help("Teller for antall automaiske avsluttede oppgaver")
    .register()

private val oppgaveDelvisAutomatisk = Counter.build()
    .name("oppgaveDelvisAutomatisk_counter")
    .help("Teller for antall delvis automaiske avsluttede oppgaver")
    .register()

private val oppgaveBeslutter = Counter.build()
    .name("oppgaveBeslutter_counter")
    .help("Teller for antall beslutter avsluttede oppgaver")
    .register()

private val ledetid = Histogram.build()
    .name("ledetid_behandling_av_oppgave")
    .help("Ledetid behandling av oppgaver")
    .register()

internal fun K9SakModell.reportMetrics(reservasjonRepository: ReservasjonRepository) {
    val oppgave = oppgave()
    if (starterSak()) {
        oppgaveOpprettet.inc()
    }

    if (oppgave.behandlingStatus == BehandlingStatus.AVSLUTTET) {
        oppgaveAvsluttet.inc()

        ledetid.observe(Duration.between(førsteEvent().eventTid, sisteEvent().eventTid).toHours().toDouble())

        val varInnomBeslutter = eventer.map { oppgave(it) }.any { it.tilBeslutter }
        if (varInnomBeslutter) {
            oppgaveBeslutter.inc()
        }
        if (reservasjonRepository.finnes(sisteEvent().eksternId!!) && !varInnomBeslutter) {
            oppgaveDelvisAutomatisk.inc()
        }

        if (!reservasjonRepository.finnes(sisteEvent().eksternId!!)) {
            oppgaveAutomatisk.inc()
        }
    }
}
