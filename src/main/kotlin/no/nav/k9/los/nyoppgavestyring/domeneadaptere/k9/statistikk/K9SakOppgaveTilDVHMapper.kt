package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk

import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.kodeverk.behandling.BehandlingStatus
import no.nav.k9.kodeverk.behandling.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

class K9SakOppgaveTilDVHMapper {

    companion object {
        val zoneId = ZoneId.of("Europe/Oslo")
    }

    fun lagBehandlinger(oppgave: Oppgave): List<Behandling> {
        val behandlingstatus = BehandlingStatus.fraKode(oppgave.hentVerdi("behandlingsstatus"))
        val behandlinger = mutableListOf<Behandling>()
        if (behandlingstatus == BehandlingStatus.AVSLUTTET) {
            if (oppgave.versjon == 0) {
                val mottattDato = LocalDateTime.parse(oppgave.hentVerdi("mottattDato")).toLocalDate()
                behandlinger.add(lagBehandling(
                    oppgave,
                    vedtaksDato = null,
                    registrertDato = mottattDato,
                    behandlingStatus = BehandlingStatus.OPPRETTET)
                )
            }
        }
        behandlinger.add(lagBehandling(oppgave, behandlingStatus = behandlingstatus))
        return behandlinger
    }

    fun lagBehandling(
        oppgave: Oppgave,
        vedtaksDato: LocalDate? = oppgave.hentVerdi("vedtaksDato")?.let { LocalDate.parse(it) },
        registrertDato: LocalDate = LocalDateTime.parse(oppgave.hentVerdi("registrertDato")).toLocalDate(),
        behandlingStatus: BehandlingStatus
    ): Behandling {
        return Behandling(
            sakId = oppgave.hentVerdi("saksnummer"),
            behandlingId = oppgave.eksternId,
            funksjonellTid = LocalDateTime.parse(oppgave.eksternVersjon).atZone(zoneId).toOffsetDateTime(),
            tekniskTid = OffsetDateTime.now(zoneId),
            mottattDato = LocalDateTime.parse(oppgave.hentVerdi("mottattDato")).toLocalDate(),
            registrertDato = registrertDato,
            vedtaksDato = vedtaksDato,
            relatertBehandlingId = null,
            vedtakId = oppgave.hentVerdi("vedtakId"),
            saksnummer = oppgave.hentVerdi("saksnummer"),
            behandlingType = oppgave.hentVerdi("behandlingTypekode")
                ?.let { BehandlingType.fraKode(it).kode },
            behandlingStatus = behandlingStatus.kode,
            resultat = oppgave.hentVerdi("resultattype"),
            resultatBegrunnelse = null, //TODO: callback mot K9?
            utenlandstilsnitt = oppgave.hentVerdi("utenlandstilsnitt")?.let { it.toBoolean() },
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
            ansvarligEnhetKode = utledEnhetskode(oppgave),
            ansvarligEnhetType = "NORG",
            datoForUttak = null, // TODO: mappes fra YtelseV1.anvist.firstOrNull()?.periode?.fom, men trengs ikke?
            datoForUtbetaling = null, //TODO: trengs ikke?
            totrinnsbehandling = oppgave.hentVerdi("totrinnskontroll").toBoolean(),
            helautomatiskBehandlet = oppgave.hentVerdi("helautomatiskBehandlet").toBoolean(),
            avsender = "K9sak",
            versjon = 1, //TODO: Ikke i bruk?
        )
    }

    private fun utledEnhetskode(oppgave: Oppgave) =
        when (FagsakYtelseType.fraKode(oppgave.hentVerdi("ytelsestype"))) {
            FagsakYtelseType.FRISINN -> "4863"
            FagsakYtelseType.PLEIEPENGER_SYKT_BARN -> "4487"
            FagsakYtelseType.PLEIEPENGER_NÆRSTÅENDE -> "4487"
            FagsakYtelseType.OMSORGSPENGER -> "4487"
            FagsakYtelseType.OMSORGSPENGER_KS -> "4487"
            FagsakYtelseType.OMSORGSPENGER_MA -> "4487"
            FagsakYtelseType.OMSORGSPENGER_AO -> "4487"
            FagsakYtelseType.OPPLÆRINGSPENGER -> "4487"
            FagsakYtelseType.UNGDOMSYTELSE -> "4487" //TODO riktig?
            //FagsakYtelseType.PÅRØRENDESYKDOM -> "4487" //Fjernet fra kontrakt 16.01.2024
            FagsakYtelseType.OBSOLETE -> "4487"
            FagsakYtelseType.UDEFINERT -> "4487"
            else -> throw IllegalStateException("Ukjent ytelsestype: ${oppgave.hentVerdi("ytelsestype")}")
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
            ytelseTypeBeskrivelse = FagsakYtelseType.fraKode(oppgave.hentVerdi("ytelsestype")).navn,
            underTypeBeskrivelse = null,
            sakStatusBeskrivelse = BehandlingStatus.fraKode(oppgave.hentVerdi("behandlingsstatus")).navn,
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