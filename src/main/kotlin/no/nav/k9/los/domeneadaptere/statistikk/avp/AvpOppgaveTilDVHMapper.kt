package no.nav.k9.los.domeneadaptere.statistikk.avp

import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.kodeverk.AvpOppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.oppgavedefinisjon.AktivitetspengerFelter
import no.nav.k9.los.domeneadaptere.statistikk.Aktør
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.ung.kodeverk.behandling.BehandlingResultatType
import no.nav.ung.kodeverk.behandling.BehandlingStatus
import no.nav.ung.kodeverk.behandling.BehandlingType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

class AvpOppgaveTilDVHMapper {

    companion object {
        val zoneId: ZoneId = ZoneId.of("Europe/Oslo")
    }

    fun lagBehandling(oppgave: Oppgave): AvpBehandling {
        val behandlingstatus = oppgave.hentVerdi(AktivitetspengerFelter.Behandling.STATUS.id)?.let { BehandlingStatus.fraKode(it) }
        val behandlingType = oppgave.hentVerdi(AktivitetspengerFelter.Behandling.TYPEKODE.id)?.let { BehandlingType.fraKode(it) }
        val resultattype = oppgave.hentVerdi(AktivitetspengerFelter.Behandling.RESULTATTYPE.id)?.let { BehandlingResultatType.fraKode(it) }

        return AvpBehandling(
            sakId = oppgave.hentVerdi(AktivitetspengerFelter.Sak.SAKSNUMMER.id),
            behandlingId = oppgave.eksternId,
            søkersAktørId = Aktør(oppgave.hentVerdi(AktivitetspengerFelter.Sak.AKTOR_ID.id)!!.toLong(), "Søker", "Søker"),
            funksjonellTid = LocalDateTime.parse(oppgave.eksternVersjon).atZone(zoneId).toOffsetDateTime(),
            tekniskTid = OffsetDateTime.now(zoneId),
            mottattDato = oppgave.hentVerdi(AktivitetspengerFelter.Sak.MOTTATT_DATO.id)?.let { LocalDateTime.parse(it).toLocalDate() },
            registrertDato = oppgave.hentVerdi(AktivitetspengerFelter.Sak.REGISTRERT_DATO.id)?.let { LocalDateTime.parse(it).toLocalDate() },
            vedtaksDato = oppgave.hentVerdi(AktivitetspengerFelter.Vedtak.DATO.id)?.let { LocalDate.parse(it) },
            saksnummer = oppgave.hentVerdi(AktivitetspengerFelter.Sak.SAKSNUMMER.id),
            behandlingType = behandlingType?.kode,
            behandlingTypeBeskrivelse = behandlingType?.navn,
            behandlingStatus = behandlingstatus?.kode,
            behandlingStatusBeskrivelse = behandlingstatus?.navn,
            resultat = resultattype?.kode,
            resultatBeskrivelse = resultattype?.navn,
            utenlandssak = null, // TODO
            beslutter = oppgave.hentVerdi(AktivitetspengerFelter.Saksbehandling.ANSVARLIG_BESLUTTER.id),
            saksbehandler = oppgave.hentVerdi(AktivitetspengerFelter.Saksbehandling.ANSVARLIG_SAKSBEHANDLER.id),
            ansvarligEnhetKode = oppgave.hentVerdi(AktivitetspengerFelter.Saksbehandling.BEHANDLENDE_ENHET.id)!!, //dvh kaller fagsystemets behandlende enhet for ansvarlig enhet. De utleder en egen behandlende enhet baser på ansvarlig saksbehandlers enhetstilhørighet
            totrinnsbehandling = oppgave.hentVerdi(AktivitetspengerFelter.Vedtak.TOTRINNSKONTROLL.id)?.toBoolean(),
            helautomatiskBehandlet = oppgave.hentVerdi(AktivitetspengerFelter.Saksbehandling.HELAUTOMATISK.id).toBoolean(),
            avsender = "K9los",
            relatertBehandlingId = null, // TODO: klage
            relatertBehandlingFagsystem = if (oppgave.oppgavetype.eksternId == AvpOppgavetypenavn.AKTIVITETSPENGERKLAGE.kode) "ung-sak" else null,
            oversendtKlageinstans = null, // TODO: klage
        )
    }
}
