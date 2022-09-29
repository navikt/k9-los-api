package no.nav.k9.nyoppgavestyring.domeneadaptere.statistikk

import no.nav.k9.nyoppgavestyring.visningoguttrekk.Oppgave
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

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
            aktorId = utledAktørId(sisteVersjon.hentVerdi("aktorId")),
            aktorer = utledAktører(sisteVersjon.hentVerdi("aktorId")),
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

    fun utledAktørId(aktørId: String?): Long? {
        val aktørIdLong = kotlin.runCatching { aktørId?.toLong() }.getOrNull()
        return aktørIdLong
    }
    fun utledAktører(aktørId: String?): List<Aktør> {
        val aktørIdLong = utledAktørId(aktørId)
        return if (aktørIdLong != null) {
            listOf(Aktør(aktørIdLong, "Søker", "Søker"))
        } else {
            listOf()
        }
    }
}