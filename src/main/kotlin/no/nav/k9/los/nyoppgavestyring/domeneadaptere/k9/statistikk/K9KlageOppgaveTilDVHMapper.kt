package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk

import no.nav.k9.klage.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.klage.kodeverk.behandling.BehandlingStatus
import no.nav.k9.klage.kodeverk.behandling.FagsakYtelseType
import no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.K9FeltIder
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.KlageEventTilOppgaveMapper
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
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
            sakId = oppgave.hentVerdi(K9FeltIder.SAKSNUMMER),
            behandlingId = oppgave.eksternId,
            funksjonellTid = LocalDateTime.parse(oppgave.eksternVersjon).atZone(zoneId).toOffsetDateTime(),
            tekniskTid = OffsetDateTime.now(zoneId),
            mottattDato = LocalDateTime.parse(oppgave.hentVerdi(K9FeltIder.MOTTATT_DATO)).toLocalDate(),
            registrertDato = LocalDateTime.parse(oppgave.hentVerdi(K9FeltIder.REGISTRERT_DATO)).toLocalDate(),
            vedtaksDato = oppgave.hentVerdi(K9FeltIder.VEDTAKSDATO)
                ?.let { LocalDate.parse(it) },
            relatertBehandlingId = oppgave.hentVerdi(K9FeltIder.PAKLAGET_BEHANDLING_UUID),
            relatertBehandlingFagsystem = utledRelatertBehandlingFagsystem(oppgave),
            vedtakId = oppgave.hentVerdi("vedtakId"),
            saksnummer = oppgave.hentVerdi(K9FeltIder.SAKSNUMMER),
            behandlingType = oppgave.hentVerdi(K9FeltIder.BEHANDLING_TYPEKODE)
                ?.let { BehandlingType.fraKode(it).kode },
            behandlingStatus = utledBehandlingStatus(oppgave),
            resultat = oppgave.hentVerdi(K9FeltIder.RESULTATTYPE),
            resultatBegrunnelse = null, //TODO: callback mot K9?
            utenlandstilsnitt = oppgave.hentVerdi("utenlandstilsnitt")?.let { it.toBoolean() },
            behandlingTypeBeskrivelse = BehandlingType.fraKode(oppgave.hentVerdi(K9FeltIder.BEHANDLING_TYPEKODE)!!).navn,
            behandlingStatusBeskrivelse = BehandlingStatus.fraKode(oppgave.hentVerdi(K9FeltIder.BEHANDLINGSSTATUS)).navn,
            resultatBeskrivelse = BehandlingResultatType.fraKode(oppgave.hentVerdi(K9FeltIder.RESULTATTYPE)).navn,
            resultatBegrunnelseBeskrivelse = null,
            utenlandstilsnittBeskrivelse = null,
            beslutter = oppgave.hentVerdi(K9FeltIder.ANSVARLIG_BESLUTTER),
            saksbehandler = oppgave.hentVerdi(K9FeltIder.ANSVARLIG_SAKSBEHANDLER),
            behandlingOpprettetAv = "system",
            behandlingOpprettetType = null,
            behandlingOpprettetTypeBeskrivelse = null,
            ansvarligEnhetKode = utledEnhetskode(oppgave),
            ansvarligEnhetType = "NORG",
            datoForUttak = null, // TODO: mappes fra YtelseV1.anvist.firstOrNull()?.periode?.fom, men trengs ikke?
            datoForUtbetaling = null, //TODO: trengs ikke?
            totrinnsbehandling = oppgave.hentVerdi(K9FeltIder.TOTRINNSKONTROLL).toBoolean(),
            helautomatiskBehandlet = oppgave.hentVerdi(K9FeltIder.HELAUTOMATISK_BEHANDLET).toBoolean(),
            oversendtKlageinstans = oppgave.hentVerdi(K9FeltIder.OVERSENDT_KLAGEINSTANS_TIDSPUNKT)?.run(LocalDateTime::parse),
            avsender = "K9klage",
            versjon = 1, //TODO: Ikke i bruk?
        )
    }

    private fun utledRelatertBehandlingFagsystem(oppgave: Oppgave) : String? {
        val verdi = oppgave.hentVerdi(
            K9FeltIder.PAKLAGET_BEHANDLINGTYPE)

        return verdi?.let {
            val påklagdBehandlingType = no.nav.k9.klage.kodeverk.behandling.BehandlingType.fraKode(it)
            when (påklagdBehandlingType) {
                no.nav.k9.klage.kodeverk.behandling.BehandlingType.TILBAKEKREVING,
                no.nav.k9.klage.kodeverk.behandling.BehandlingType.REVURDERING_TILBAKEKREVING -> "k9-tilbake"
                else -> "k9-sak"
            }
        }
    }

    private fun utledBehandlingStatus(oppgave: Oppgave): String {
        return if (oppgave.hentListeverdi(K9FeltIder.AKTIVT_AKSJONSPUNKT).contains(KlageEventTilOppgaveMapper.KLAGE_PREFIX + AksjonspunktDefinisjon.AUTO_OVERFØRT_NK.kode)) {
            "OVERFORT_KLAGE_ANKE"
        } else {
            BehandlingStatus.fraKode(oppgave.hentVerdi(K9FeltIder.BEHANDLINGSSTATUS)).kode
        }
    }

    fun lagSak(oppgave: Oppgave): Sak {
        return Sak(
            saksnummer = oppgave.hentVerdi(K9FeltIder.SAKSNUMMER)!!,
            sakId = oppgave.eksternId,
            funksjonellTid = LocalDateTime.parse(oppgave.eksternVersjon).atZone(K9SakOppgaveTilDVHMapper.zoneId).toOffsetDateTime(),
            tekniskTid = OffsetDateTime.now(zoneId),
            opprettetDato = LocalDateTime.parse(oppgave.hentVerdi(K9FeltIder.MOTTATT_DATO)).toLocalDate(), // TODO må finne ut om dette er riktig
            aktorId = utledAktørId(oppgave.hentVerdi(K9FeltIder.AKTOR_ID)),
            aktorer = utledAktører(oppgave.hentVerdi(K9FeltIder.AKTOR_ID)),
            ytelseType = oppgave.hentVerdi(K9FeltIder.YTELSESTYPE),
            underType = null,
            sakStatus = oppgave.hentVerdi(K9FeltIder.BEHANDLINGSSTATUS),
            ytelseTypeBeskrivelse = FagsakYtelseType.fraKode(oppgave.hentVerdi(K9FeltIder.YTELSESTYPE)).navn,
            underTypeBeskrivelse = null,
            sakStatusBeskrivelse = BehandlingStatus.fraKode(oppgave.hentVerdi(K9FeltIder.BEHANDLINGSSTATUS)).navn,
            avsender = "K9los",
            versjon = 1 // TODO blir dette riktig?
        )
    }

    private fun utledEnhetskode(oppgave: Oppgave) =
        when (FagsakYtelseType.fraKode(oppgave.hentVerdi(K9FeltIder.YTELSESTYPE))) {
            FagsakYtelseType.FRISINN -> "4863"
            FagsakYtelseType.PLEIEPENGER_SYKT_BARN -> "4487"
            FagsakYtelseType.PLEIEPENGER_NÆRSTÅENDE -> "4487"
            FagsakYtelseType.OMSORGSPENGER -> "4487"
            FagsakYtelseType.OMSORGSPENGER_KS -> "4487"
            FagsakYtelseType.OMSORGSPENGER_MA -> "4487"
            FagsakYtelseType.OMSORGSPENGER_AO -> "4487"
            FagsakYtelseType.OPPLÆRINGSPENGER -> "4487"
            FagsakYtelseType.PÅRØRENDESYKDOM -> "4487"
            FagsakYtelseType.SVANGERSKAPSPENGER -> "4487"
            FagsakYtelseType.OBSOLETE -> "4487"
            FagsakYtelseType.UDEFINERT -> "4487"
            else -> throw IllegalStateException("Ukjent ytelsestype: ${oppgave.hentVerdi(K9FeltIder.YTELSESTYPE)}")
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