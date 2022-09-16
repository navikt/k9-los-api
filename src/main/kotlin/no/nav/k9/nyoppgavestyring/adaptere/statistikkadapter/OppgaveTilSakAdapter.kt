package no.nav.k9.nyoppgavestyring.adaptere.statistikkadapter

import no.nav.k9.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.statistikk.kontrakter.Sak
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

class OppgaveTilSakAdapter {

    companion object {
        val zoneOffset = ZoneOffset.of("Europe/Oslo")
    }

    fun lagSak(oppgaveversjoner: Set<Oppgave>): Sak {
        val sisteVersjon = oppgaveversjoner.last()
        return Sak(
            saksnummer = sisteVersjon.eksternId,
            sakId = sisteVersjon.eksternId,
            funksjonellTid = OffsetDateTime.of(LocalDateTime.parse(sisteVersjon.eksternVersjon), zoneOffset),
            tekniskTid = OffsetDateTime.now(zoneOffset),
            opprettetDato = LocalDateTime.parse(oppgaveversjoner.first().eksternVersjon).toLocalDate(),
            aktorId = sisteVersjon.hentVerdi("aktorId")?.toLong(),
            aktorer = listOf(),
            ytelseType = sisteVersjon.hentVerdi("ytelsestype"),
            underType = null,
            sakStatus = sisteVersjon.hentVerdi("behandlingsstatus"),
            ytelseTypeBeskrivelse = null,
            underTypeBeskrivelse = null,
            sakStatusBeskrivelse = null,
            avsender = "K9los",
            versjon = 1 // TODO blir dette riktig?
        )
    }
}