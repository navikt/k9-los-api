package no.nav.k9.los.domene.lager.oppgave.v2

import assertk.assertThat
import assertk.assertions.*
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

internal class BehandlingTest {

    val oppgave1 = OpprettOppgave(tidspunkt = LocalDateTime.now().minusHours(12), oppgaveKode = "OPPGAVE1", frist = null)
    val oppgave2 = OpprettOppgave(tidspunkt = LocalDateTime.now().minusHours(1), oppgaveKode = "OPPGAVE2", frist = null)
    val nå = LocalDateTime.now()

    @Test
    fun `FerdigstillOppgave ferdigstiller ikke etterfolgende oppgaver ved oppgitt OppgaveKode`() {
        val behandling = opprettBehandling()

        behandling.nyHendelse(oppgave1)
        behandling.nyHendelse(oppgave2)

        behandling.nyHendelse(
            FerdigstillOppgave(
                tidspunkt = LocalDateTime.MAX,
                oppgaveKode = oppgave1.oppgaveKode
            )
        )
        assertThat(behandling.hent(oppgave1).oppgaveStatus).isEqualTo(OppgaveStatus.FERDIGSTILT)
        assertThat(behandling.hent(oppgave2).oppgaveStatus).isEqualTo(OppgaveStatus.OPPRETTET)
        assertThat(behandling.oppgaver().filter { it.erAktiv() }.map { it.oppgaveKode }).containsExactly(oppgave2.oppgaveKode)
    }

    @Test
    fun `FerdigstillOppgave ferdigstiller gjeldene og foregående oppgaver ved oppgitt OppgaveKode`() {
        val behandling = opprettBehandling()
        behandling.nyHendelse(oppgave1)
        behandling.nyHendelse(oppgave2)

        behandling.nyHendelse(
            FerdigstillOppgave(
                tidspunkt = LocalDateTime.MAX,
                oppgaveKode = oppgave2.oppgaveKode
            )
        )
        assertThat(behandling.oppgaver().map { it.oppgaveStatus }).containsOnly(OppgaveStatus.FERDIGSTILT)
        assertThat(behandling.harAktiveOppgaver()).isFalse()
    }

    @Test
    fun `FerdigstillOppgave uten oppgavekode ferdigstiller alle aktive oppgaver`() {
        val behandling = opprettBehandling()
        behandling.nyHendelse(oppgave1)
        behandling.nyHendelse(oppgave2)

        behandling.nyHendelse(
            FerdigstillOppgave(
                tidspunkt = LocalDateTime.MAX,
                oppgaveKode = null
            )
        )
        assertThat(behandling.oppgaver().map { it.oppgaveStatus }).containsOnly(OppgaveStatus.FERDIGSTILT)
        assertThat(behandling.oppgaver().filter { it.erAktiv() }).isEmpty()
    }

    @Test
    fun `AvbrytOppgave avbryter ikke etterfolgende oppgaver ved oppgitt OppgaveKode`() {
        val behandling = opprettBehandling()

        behandling.nyHendelse(oppgave1)
        behandling.nyHendelse(oppgave2)

        behandling.nyHendelse(
            AvbrytOppgave(
                tidspunkt = LocalDateTime.MAX,
                oppgaveKode = oppgave1.oppgaveKode
            )
        )
        assertThat(behandling.hent(oppgave1).oppgaveStatus).isEqualTo(OppgaveStatus.AVBRUTT)
        assertThat(behandling.hent(oppgave2).oppgaveStatus).isEqualTo(OppgaveStatus.OPPRETTET)
        assertThat(behandling.oppgaver().filter { it.erAktiv() }.map { it.oppgaveKode }).containsExactly(oppgave2.oppgaveKode)
    }

    @Test
    fun `AvbrytOppgave uten oppgavekode avbryter alle aktive oppgave, for å tillate enklere bruk så lenge kun én oppgave er aktiv om gangen`() {
        val behandling = opprettBehandling()
        behandling.nyHendelse(oppgave1)
        behandling.nyHendelse(oppgave2)

        behandling.nyHendelse(
            AvbrytOppgave(
                tidspunkt = LocalDateTime.MAX,
                oppgaveKode = null
            )
        )
        assertThat(behandling.oppgaver().map { it.oppgaveStatus }).containsOnly(OppgaveStatus.AVBRUTT)
        assertThat(behandling.oppgaver().filter { it.erAktiv() }).isEmpty()
    }

    @Test
    fun `AvbrytOppgave avbryter kun gjeldene oppgave ved oppgitt OppgaveKode`() {
        val behandling = opprettBehandling()
        behandling.nyHendelse(oppgave1)
        behandling.nyHendelse(oppgave2)

        behandling.nyHendelse(
            AvbrytOppgave(
                tidspunkt = LocalDateTime.MAX,
                oppgaveKode = oppgave2.oppgaveKode
            )
        )
        assertThat(behandling.oppgaver().map { it.oppgaveStatus }).containsExactly(OppgaveStatus.OPPRETTET, OppgaveStatus.AVBRUTT)
        assertThat(behandling.harAktiveOppgaver()).isTrue()
    }

    @Test // Dette må være mulig fordi f.eks beslutter kan sende saksbehandler tilbake, forbi tidligere ferdigstilte aksjonspunkter
    fun `AvbrytOppgave kan endre den siste ferdigstilt oppgaven til avbrutt`() {
        val behandling = opprettBehandling()
        behandling.nyHendelse(oppgave1)
        behandling.nyHendelse(FerdigstillOppgave(tidspunkt = nå, oppgaveKode = oppgave1.oppgaveKode))

        behandling.nyHendelse(oppgave2.copy(tidspunkt = nå.plusDays(1)))
        behandling.nyHendelse(FerdigstillOppgave(tidspunkt = nå.plusDays(1), oppgaveKode = oppgave2.oppgaveKode))
        behandling.nyHendelse(oppgave2.copy(tidspunkt = nå.plusDays(2)))
        behandling.nyHendelse(FerdigstillOppgave(tidspunkt = nå.plusDays(2), oppgaveKode = oppgave2.oppgaveKode))

        behandling.nyHendelse(AvbrytOppgave(tidspunkt = nå.plusDays(3), oppgaveKode = oppgave1.oppgaveKode))
        behandling.nyHendelse(AvbrytOppgave(tidspunkt = nå.plusDays(3), oppgaveKode = oppgave2.oppgaveKode))

        assertThat(behandling.hentAlle(oppgave1).map { it.oppgaveStatus }).containsOnly(OppgaveStatus.AVBRUTT)
        assertThat(behandling.hentAlle(oppgave2).map { it.oppgaveStatus }).containsExactly(OppgaveStatus.FERDIGSTILT, OppgaveStatus.AVBRUTT)

        assertThat(behandling.harAktiveOppgaver()).isFalse()
    }

    @Test
    fun `Ferdigstilling av behandling lukker alle aktive oppgaver og setter ferdigstilt-tidspunkt på behandling`() {
        val behandling = opprettBehandling()

        behandling.nyHendelse(oppgave1)
        val ferdigstiltTidspunkt = LocalDateTime.now()
        behandling.nyHendelse(FerdigstillBehandling(tidspunkt = ferdigstiltTidspunkt))

        assertThat(behandling.oppgaver().filter { it.erAktiv() }).isEmpty()
        assertThat(behandling.oppgaver().map { it.oppgaveStatus }).containsOnly(OppgaveStatus.FERDIGSTILT)
        assertThat(behandling.oppgaver().map { it.ferdigstilt?.tidspunkt }).containsOnly(ferdigstiltTidspunkt)
        assertThat(behandling.ferdigstilt).isEqualTo(ferdigstiltTidspunkt)
    }

    @Test
    fun `Ferdigstilling av behandling endrer ikke tidligere ferdigstilte eller avbrutte oppgaver`() {
        val behandling = opprettBehandling()

        behandling.nyHendelse(oppgave1)
        val ferdigstiltOppgavetidspunkt = LocalDateTime.now().minusDays(1)
        behandling.nyHendelse(
            FerdigstillOppgave(
                tidspunkt = ferdigstiltOppgavetidspunkt,
                oppgaveKode = oppgave1.oppgaveKode,
                ansvarligSaksbehandlerIdent = "Z12345",
            )
        )
        behandling.nyHendelse(oppgave2)
        behandling.nyHendelse(
            AvbrytOppgave(
            tidspunkt = ferdigstiltOppgavetidspunkt,
            oppgaveKode = oppgave2.oppgaveKode,
        )
        )

        val ferdigstiltTidspunkt = LocalDateTime.now()
        behandling.ferdigstill(FerdigstillBehandling(tidspunkt = ferdigstiltTidspunkt))

        val o1 = behandling.hent(oppgave1)
        val o2 = behandling.hent(oppgave2)

        assertThat(behandling.oppgaver().filter { it.erAktiv() }).isEmpty()
        assertThat(o1.oppgaveStatus).isEqualTo(OppgaveStatus.FERDIGSTILT)
        assertThat(o1.ferdigstilt?.tidspunkt).isEqualTo(ferdigstiltOppgavetidspunkt)
        assertThat(o2.oppgaveStatus).isEqualTo(OppgaveStatus.AVBRUTT)
        assertThat(o2.ferdigstilt).isNull()
        assertThat(behandling.ferdigstilt).isEqualTo(ferdigstiltTidspunkt)
    }

    @Test
    fun `FerdigstillOppgave skal ignoreres hvis det allerede finnes ferdigstillelse med samme på timestamp uavhengig av om oppgaveKode er satt`() {
        val behandling = opprettBehandling()

        behandling.nyHendelse(oppgave1)
        val ferdigstillelseUtenKode = FerdigstillOppgave(tidspunkt = LocalDateTime.now(), oppgaveKode = null)
        assertThat(behandling.harBehandletFerdigstillelseAvOppgave(ferdigstillelseUtenKode)).isFalse()
        behandling.nyHendelse(ferdigstillelseUtenKode)

        assertThat(behandling.harBehandletFerdigstillelseAvOppgave(ferdigstillelseUtenKode)).isTrue()

        behandling.nyHendelse(oppgave1.copy(tidspunkt = LocalDateTime.now().plusMinutes(1)))
        val ferdigstillelseMedKode = FerdigstillOppgave(tidspunkt = LocalDateTime.now().plusMinutes(1), oppgaveKode = oppgave1.oppgaveKode)
        assertThat(behandling.harBehandletFerdigstillelseAvOppgave(ferdigstillelseMedKode)).isFalse()
        behandling.nyHendelse(ferdigstillelseMedKode)

        assertThat(behandling.harBehandletFerdigstillelseAvOppgave(ferdigstillelseMedKode)).isTrue()
    }

    @Test
    fun `FerdigstillOppgave skal brukes selv om det finnes oppgave med oppgavekode`() {
        val behandling = opprettBehandling().apply { nyHendelse(oppgave1) }

        val ferdigstillelse1 = FerdigstillOppgave(tidspunkt = LocalDateTime.now(), oppgaveKode = oppgave1.oppgaveKode)
        assertThat(behandling.harBehandletFerdigstillelseAvOppgave(ferdigstillelse1)).isFalse()
        behandling.nyHendelse(ferdigstillelse1)

        val ferdigstillelse2 = FerdigstillOppgave(tidspunkt = LocalDateTime.now().plusMinutes(1), oppgaveKode = oppgave1.oppgaveKode)
        assertThat(behandling.harBehandletFerdigstillelseAvOppgave(ferdigstillelse2)).isFalse()
    }

    @Test
    fun `Duplikatsjekk skal kunne handtere stor forskjell i timestamp`() {
        val behandling = opprettBehandling().apply {
            nyHendelse(oppgave1.copy(tidspunkt = LocalDateTime.MIN))
        }

        val ferdigstillelse1 = FerdigstillOppgave(tidspunkt = LocalDateTime.MAX, oppgaveKode = oppgave1.oppgaveKode)
        assertThat(behandling.harBehandletFerdigstillelseAvOppgave(ferdigstillelse1)).isFalse()
        behandling.nyHendelse(ferdigstillelse1)

        behandling.nyHendelse(oppgave1.copy(tidspunkt = LocalDateTime.MAX))
    }

    @Test
    fun `timestampsammenligning med liten forskjell må tåle variasjon i presisjon`() {
        val eventTid = LocalDateTime.parse("2022-12-02T16:23:32.725600600")
        val dbTid = LocalDateTime.parse("2022-12-02T16:23:32.726")
        assertThat(eventTid.equals(dbTid)).isFalse()
        assertThat(eventTid == dbTid).isFalse()
        assertThat(eventTid.equalsWithPrecision(dbTid)).isTrue()
    }

    @Test
    fun `timestampsammenligning skal tåle timestamps med stor forskjell`() {
        val dbTid = LocalDateTime.MAX
        val eventTid = LocalDateTime.MIN
        assertThat(eventTid.equalsWithPrecision(dbTid)).isFalse()
    }

    private fun Behandling.hent(oppgave: OpprettOppgave): OppgaveV2 {
        return oppgaver().first { it.oppgaveKode == oppgave.oppgaveKode }
    }

    private fun Behandling.hentAlle(oppgave: OpprettOppgave): List<OppgaveV2> {
        return oppgaver().filter { it.oppgaveKode == oppgave.oppgaveKode }
    }

    private fun opprettBehandling() = Behandling.ny(
        eksternReferanse = UUID.randomUUID().toString(),
        fagsystem = Fagsystem.K9SAK,
        ytelseType = FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
        behandlingType = " ",
        søkersId = Ident("123456", Ident.IdType.AKTØRID),
        opprettet = LocalDateTime.now()
    )
}