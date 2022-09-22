package no.nav.k9.nyoppgavestyring.domeneadaptere.statistikk

import no.nav.k9.nyoppgavestyring.visningoguttrekk.Oppgave
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class OppgaveTilSakMapper {

    companion object {
        val zoneId = ZoneId.of("Europe/Oslo")
    }

    fun lagSak(oppgaveversjoner: Set<Oppgave>): Sak {
        val sisteVersjon = oppgaveversjoner.last()
        return Sak(
            saksnummer = sisteVersjon.eksternId,
            sakId = sisteVersjon.eksternId,
            funksjonellTid = LocalDateTime.parse(sisteVersjon.eksternVersjon).atZone(OppgaveTilBehandlingMapper.zoneId).toOffsetDateTime(),
            tekniskTid = OffsetDateTime.now(zoneId),
            opprettetDato = oppgaveversjoner.first().endretTidspunkt.toLocalDate(), // TODO må finne ut om dette er riktig
            aktorId = sisteVersjon.hentVerdi("aktorId")?.toLong(),
            aktorer = listOf(Aktør(sisteVersjon.hentVerdi("aktorId")!!.toLong(), "Søker", "Søker")), // TODO dette må gjøres mer robust
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