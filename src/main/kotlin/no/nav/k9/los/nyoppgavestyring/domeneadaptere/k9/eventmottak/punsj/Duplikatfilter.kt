package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj

import org.slf4j.LoggerFactory
import java.time.LocalDateTime

object Duplikatfilter {

    private val log = LoggerFactory.getLogger(Duplikatfilter::class.java)

    fun fjernDuplikater(eventer: Collection<PunsjEventDto>): Set<PunsjEventDto> {
        val gruppert = eventer.groupBy { it.eventTid }
        val resultat: MutableSet<PunsjEventDto> = mutableSetOf()
        val tidspunktMedFlere: MutableSet<LocalDateTime> = mutableSetOf()
        for ((tidspunkt, eventerMedLikTid) in gruppert) {
            if (eventerMedLikTid.size == 1) {
                resultat.addAll(eventerMedLikTid)
            } else {
                val leggesTil: MutableSet<PunsjEventDto> = mutableSetOf()
                for (event in eventerMedLikTid) {
                    if (leggesTil.any { erFunksjoneltLike(event, it) }) {
                        log.info("Ignorerer en funksjonelt duplikat hendelse. Gjaldt eksternId ${event.eksternId} eventTid ${event.eventTid}")
                    } else {
                        if (leggesTil.isNotEmpty()) {
                            val ulikeFelter = ulikeFelter(leggesTil.first(), event)
                            log.warn("Har ulike eventer på samme tidspunkt. Gjelder eksternId ${event.eksternId} og tidspunkt ${tidspunkt}. Avvik i felt: $ulikeFelter")
                        }
                        leggesTil.add(event)
                    }
                }
                resultat.addAll(leggesTil)
                if (leggesTil.size > 1) {
                    tidspunktMedFlere.add(tidspunkt)
                }
            }
        }
        if (tidspunktMedFlere.isNotEmpty()) {
            log.warn("Har ulike eventer på samme tidspunkt. Gjelder eksternId ${resultat.first().eksternId} og tidspunkt ${tidspunktMedFlere}")
        }
        return resultat
    }

    fun erFunksjoneltLike(a: PunsjEventDto, b: PunsjEventDto): Boolean {
        if (a == b) {
            return true
        }
        if (a.eventTid != b.eventTid) {
            return false
        }
        return false
    }


    fun ulikeFelter(a: PunsjEventDto, b: PunsjEventDto): List<String> {
        val avvikendeFelt: MutableList<String> = mutableListOf()
        if (a.eksternId != b.eksternId) {
            avvikendeFelt.add("eksternId")
        }
        if (a.journalpostId != b.journalpostId) {
            avvikendeFelt.add("journalpostId")
        }
        if (a.eventTid != b.eventTid) {
            avvikendeFelt.add("eventTid")
        }
        if (a.status != b.status) {
            avvikendeFelt.add("status")
        }
        if (a.aktørId != b.aktørId) {
            avvikendeFelt.add("aktørId")
        }
        if (a.aksjonspunktKoderMedStatusListe != b.aksjonspunktKoderMedStatusListe) {
            avvikendeFelt.add("aksjonspunktKoderMedStatusListe")
        }
        if (a.pleietrengendeAktørId != b.pleietrengendeAktørId) {
            avvikendeFelt.add("pleietrengendeAktørId")
        }
        if (a.type != b.type) {
            avvikendeFelt.add("type")
        }
        if (a.ytelse != b.ytelse) {
            avvikendeFelt.add("ytelse")
        }
        if (a.sendtInn != b.sendtInn) {
            avvikendeFelt.add("sendtInn")
        }
        if (a.ferdigstiltAv != b.ferdigstiltAv) {
            avvikendeFelt.add("ferdigstiltAv")
        }
        if (a.journalførtTidspunkt != b.journalførtTidspunkt) {
            avvikendeFelt.add("journalførtTidspunkt")
        }
        return avvikendeFelt
    }
}
