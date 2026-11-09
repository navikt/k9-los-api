package no.nav.k9.los.domeneadaptere.statistikk.ung

import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.kodeverk.UpyOppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.oppgavedefinisjon.UngdomsprogramytelsenFelter
import no.nav.k9.los.domeneadaptere.statistikk.Aktør
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.ung.kodeverk.behandling.BehandlingResultatType
import no.nav.ung.kodeverk.behandling.BehandlingStatus
import no.nav.ung.kodeverk.behandling.BehandlingType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

class UngOppgaveTilDVHMapper {

    companion object {
        val zoneId: ZoneId = ZoneId.of("Europe/Oslo")
    }

    fun lagBehandling(oppgave: Oppgave): UngBehandling {
        val behandlingstatus = oppgave.hentVerdi(UngdomsprogramytelsenFelter.Behandling.STATUS.id)?.let { BehandlingStatus.fraKode(it) }
        val behandlingType = oppgave.hentVerdi(UngdomsprogramytelsenFelter.Behandling.TYPEKODE.id)?.let { BehandlingType.fraKode(it) }
        val resultattype = oppgave.hentVerdi(UngdomsprogramytelsenFelter.Behandling.RESULTATTYPE.id)?.let { BehandlingResultatType.fraKode(it) }

        return UngBehandling(
            sakId = oppgave.hentVerdi(UngdomsprogramytelsenFelter.Sak.SAKSNUMMER.id),
            behandlingId = oppgave.eksternId,
            søkersAktørId = Aktør(oppgave.hentVerdi(UngdomsprogramytelsenFelter.Sak.AKTOR_ID.id)!!.toLong(), "Søker", "Søker"),
            funksjonellTid = LocalDateTime.parse(oppgave.eksternVersjon).atZone(zoneId).toOffsetDateTime(),
            tekniskTid = OffsetDateTime.now(zoneId),
            mottattDato = oppgave.hentVerdi(UngdomsprogramytelsenFelter.Sak.MOTTATT_DATO.id)?.let { LocalDateTime.parse(it).toLocalDate() },
            registrertDato = oppgave.hentVerdi(UngdomsprogramytelsenFelter.Sak.REGISTRERT_DATO.id)?.let { LocalDateTime.parse(it).toLocalDate() },
            vedtaksDato = oppgave.hentVerdi(UngdomsprogramytelsenFelter.Vedtak.DATO.id)?.let { LocalDate.parse(it) },
            saksnummer = oppgave.hentVerdi(UngdomsprogramytelsenFelter.Sak.SAKSNUMMER.id),
            behandlingType = behandlingType?.kode,
            behandlingTypeBeskrivelse = behandlingType?.navn,
            behandlingStatus = behandlingstatus?.kode,
            behandlingStatusBeskrivelse = behandlingstatus?.navn,
            resultat = resultattype?.kode,
            resultatBeskrivelse = resultattype?.navn,
            utenlandssak = null, // TODO
            beslutter = oppgave.hentVerdi(UngdomsprogramytelsenFelter.Saksbehandling.ANSVARLIG_BESLUTTER.id),
            saksbehandler = oppgave.hentVerdi(UngdomsprogramytelsenFelter.Saksbehandling.ANSVARLIG_SAKSBEHANDLER.id),
            ansvarligEnhetKode = oppgave.hentVerdi(UngdomsprogramytelsenFelter.Vedtak.BEHANDLENDE_ENHET.id), //dvh kaller fagsystemets behandlende enhet for ansvarlig enhet. De utleder en egen behandlende enhet baser på ansvarlig saksbehandlers enhetstilhørighet
            totrinnsbehandling = oppgave.hentVerdi(UngdomsprogramytelsenFelter.Vedtak.TOTRINNSKONTROLL.id)?.toBoolean(),
            helautomatiskBehandlet = null, // TODO
            avsender = "K9los",
            relatertBehandlingId = null, // TODO: klage
            relatertBehandlingFagsystem = if (oppgave.oppgavetype.eksternId == UpyOppgavetypenavn.UNGDOMSPROGRAMYTELSENKLAGE.kode) "ung-sak" else null,
            oversendtKlageinstans = null, // TODO: klage
        )
    }
}
