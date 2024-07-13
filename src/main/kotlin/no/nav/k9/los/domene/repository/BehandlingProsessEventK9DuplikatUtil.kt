package no.nav.k9.los.domene.repository

import no.nav.k9.los.domene.lager.oppgave.v2.BehandlingsmigreringTjeneste
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventDto
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

object BehandlingProsessEventK9DuplikatUtil {

    private val log = LoggerFactory.getLogger(BehandlingsmigreringTjeneste::class.java)

    fun fjernDuplikater(eventer : Collection<BehandlingProsessEventDto>) : Set<BehandlingProsessEventDto> {
        val gruppert = eventer.groupBy { it.eventTid }
        val resultat : MutableSet<BehandlingProsessEventDto> = mutableSetOf()
        var tidspunktMedFlere : MutableSet<LocalDateTime> = mutableSetOf()
        for ((tidspunkt, eventerMedLikTid) in gruppert) {
            if (eventerMedLikTid.size == 1){
                resultat.addAll(eventerMedLikTid)
            } else {
                val leggesTil: MutableSet<BehandlingProsessEventDto> = mutableSetOf()
                for (event in eventerMedLikTid) {
                    if (leggesTil.any { erFunksjoneltLike(event, it) }) {
                        log.info("Ignorerer en funksjonelt duplikat hendelse. Gjaldt eksternId ${event.eksternId} eventTid ${event.eventTid}")
                    } else {
                        leggesTil.add(event)
                    }
                }
                resultat.addAll(leggesTil)
                if (leggesTil.size > 1) {
                    tidspunktMedFlere.add(tidspunkt)
                }
            }
        }
        if (tidspunktMedFlere.isNotEmpty()){
            log.warn("Har ulike eventer på samme tidspunkt. Gjelder eksternId ${resultat.first().eksternId} og tidspunkt ${tidspunktMedFlere}")
        }
        return resultat
    }

    fun erFunksjoneltLike(a : BehandlingProsessEventDto, b: BehandlingProsessEventDto) : Boolean{
        if (a == b) {
            return true;
        }
        if (a.eventTid != b.eventTid){
            return false;
        }

        if (a.resultatType != null && b.resultatType == null){
            return a.copy(resultatType = null) == b;
        }
        if (b.resultatType != null && a.resultatType == null){
            return b.copy(resultatType = null) == a;
        }

        return false;
    }
}