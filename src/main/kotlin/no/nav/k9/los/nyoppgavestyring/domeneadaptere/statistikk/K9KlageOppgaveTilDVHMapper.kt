package no.nav.k9.los.nyoppgavestyring.domeneadaptere.statistikk

import no.nav.k9.klage.kodeverk.behandling.BehandlingStatus
import no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktKodeDefinisjon
import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.los.domene.modell.BehandlingType
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

class K9KlageOppgaveTilDVHMapper {

    companion object {
        val zoneId = ZoneId.of("Europe/Oslo")
    }

    fun lagBehandling(oppgave: Oppgave): Behandling {
        return Behandling(
            sakId = oppgave.hentVerdi("saksnummer"),
            behandlingId = oppgave.eksternId,
            funksjonellTid = LocalDateTime.parse(oppgave.eksternVersjon).atZone(zoneId).toOffsetDateTime(),
            tekniskTid = OffsetDateTime.now(zoneId),
            mottattDato = LocalDateTime.parse(oppgave.hentVerdi("mottattDato")).toLocalDate(),
            registrertDato = LocalDateTime.parse(oppgave.hentVerdi("registrertDato")).toLocalDate(),
            vedtaksDato = oppgave.hentVerdi("vedtaksDato")
                ?.let { LocalDate.parse(it) },
            relatertBehandlingId = null,
            vedtakId = oppgave.hentVerdi("vedtakId"), //TODO: callback mot K9? evt vedtakstopic, YtelseV1.vedtakReferanse
            saksnummer = oppgave.hentVerdi("saksnummer"),
            behandlingType = oppgave.hentVerdi("behandlingTypekode")
                ?.let { BehandlingType.fraKode(it).navn },
            behandlingStatus = BehandlingStatus.fraKode(oppgave.hentVerdi("behandlingsstatus")).navn,
            resultat = oppgave.hentVerdi("resultattype"),
            resultatBegrunnelse = null, //TODO: callback mot K9?
            utenlandstilsnitt = null, //Ikke i bruk i k9-klage
            behandlingTypeBeskrivelse = BehandlingType.fraKode(oppgave.hentVerdi("behandlingTypekode")!!).navn,
            behandlingStatusBeskrivelse = BehandlingStatus.fraKode(oppgave.hentVerdi("behandlingsstatus")).navn,
            resultatBeskrivelse = BehandlingResultatType.fraKode(oppgave.hentVerdi("resultattype")).navn,
            resultatBegrunnelseBeskrivelse = null,
            utenlandstilsnittBeskrivelse = null,
            beslutter = oppgave.hentVerdi("ansvarligBeslutter"),
            saksbehandler = oppgave.hentVerdi("ansvarligSaksbehandler"),
            behandlingOpprettetAv = "system",
            behandlingOpprettetType = null,
            behandlingOpprettetTypeBeskrivelse = null,
            ansvarligEnhetKode = oppgave.hentVerdi("behandlendeEnhet") ?: "SRV", //TODO: enhetskodene mangler p.t.
            ansvarligEnhetType = "NORG",
            behandlendeEnhetKode = oppgave.hentVerdi("behandlendeEnhet") ?: "SRV", //TODO: enhetskodene mangler p.t.
            behandlendeEnhetType = "NORG",
            datoForUttak = null, // TODO: mappes fra YtelseV1.anvist.firstOrNull()?.periode?.fom, men trengs ikke?
            datoForUtbetaling = null, //TODO: trengs ikke?
            totrinnsbehandling = oppgave.hentVerdi("totrinnskontroll").toBoolean(),
            helautomatiskBehandlet = oppgave.hentVerdi("helautomatiskBehandlet").toBoolean(),
            avsender = "K9klage",
            versjon = 1, //TODO: Ikke i bruk?
        )
    }

    fun lagSak(oppgave: Oppgave): Sak {
        return Sak(
            saksnummer = oppgave.hentVerdi("saksnummer")!!,
            sakId = oppgave.eksternId,
            funksjonellTid = LocalDateTime.parse(oppgave.eksternVersjon).atZone(K9SakOppgaveTilDVHMapper.zoneId).toOffsetDateTime(),
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

    private fun utledAktørId(aktørId: String?): Long? {
        val aktørIdLong = kotlin.runCatching { aktørId?.toLong() }.getOrNull()
        return aktørIdLong
    }
    private fun utledAktører(aktørId: String?): List<Aktør> {
        val aktørIdLong = utledAktørId(aktørId)
        return if (aktørIdLong != null) {
            listOf(Aktør(aktørIdLong, "Søker", "Søker"))
        } else {
            listOf()
        }
    }

}