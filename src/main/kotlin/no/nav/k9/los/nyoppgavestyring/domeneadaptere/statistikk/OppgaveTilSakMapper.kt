package no.nav.k9.los.nyoppgavestyring.domeneadaptere.statistikk

import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

class OppgaveTilSakMapper {

    companion object {
        val zoneId = ZoneId.of("Europe/Oslo")
    }

    fun lagSak(oppgave: Oppgave): Sak {
        return Sak(
            saksnummer = oppgave.hentVerdi("saksnummer")!!,
            sakId = oppgave.eksternId,
            funksjonellTid = LocalDateTime.parse(oppgave.eksternVersjon).atZone(OppgaveTilBehandlingMapper.zoneId).toOffsetDateTime(),
            tekniskTid = OffsetDateTime.now(zoneId),
            opprettetDato = LocalDateTime.parse(oppgave.hentVerdi("mottattDato")).toLocalDate(), // TODO må finne ut om dette er riktig
            aktorId = utledAktørId(oppgave.hentVerdi("aktorId")),
            aktorer = utledAktører(oppgave.hentVerdi("aktorId")),
            ytelseType = oppgave.hentVerdi("ytelsestype"),
            underType = null,
            sakStatus = oppgave.hentVerdi("behandlingsstatus"),
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