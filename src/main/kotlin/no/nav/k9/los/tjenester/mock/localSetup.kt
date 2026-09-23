package no.nav.k9.los.tjenester.mock

import kotlinx.coroutines.runBlocking
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.klage.kontrakt.behandling.oppgavetillos.Aksjonspunkttilstand
import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.kodeverk.behandling.BehandlingStegType
import no.nav.k9.kodeverk.behandling.BehandlingÅrsakType
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktKodeDefinisjon
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.kodeverk.behandling.aksjonspunkt.Venteårsak
import no.nav.k9.kodeverk.uttak.SøknadÅrsak
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.domeneadaptere.eventmottak.EventHendelse
import no.nav.k9.los.domeneadaptere.eventmottak.k9.klage.K9KlageEventDto
import no.nav.k9.los.domeneadaptere.eventmottak.k9.klage.K9KlageEventHandler
import no.nav.k9.los.domeneadaptere.eventmottak.k9.punsj.K9PunsjEventDto
import no.nav.k9.los.domeneadaptere.eventmottak.k9.punsj.K9PunsjEventHandler
import no.nav.k9.los.domeneadaptere.eventmottak.k9.sak.K9SakEventDto
import no.nav.k9.los.domeneadaptere.eventmottak.k9.sak.K9SakEventHandler
import no.nav.k9.los.domeneadaptere.eventmottak.k9.tilbakekrav.AksjonspunktDefinisjonK9Tilbake
import no.nav.k9.los.domeneadaptere.eventmottak.k9.tilbakekrav.K9TilbakeEventDto
import no.nav.k9.los.domeneadaptere.eventmottak.k9.tilbakekrav.K9TilbakeEventHandler
import no.nav.k9.los.domeneadaptere.eventmottak.ung.sak.UngSakEventDto
import no.nav.k9.los.domeneadaptere.eventmottak.ung.sak.UngSakEventHandler
import no.nav.k9.los.domeneadaptere.eventlager.EventNøkkel
import no.nav.k9.los.domeneadaptere.eventtiloppgave.EventTilOppgaveAdapter
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.kodeverk.AktBehandlendeEnhet
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.kodeverk.BehandlingStatus
import no.nav.k9.los.kodeverk.BehandlingType
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.kodeverk.K9FagsakYtelseType
import no.nav.k9.los.domeneadaptere.eventlager.Fagsystem
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto
import no.nav.k9.sak.typer.AktørId
import no.nav.k9.sak.typer.JournalpostId
import no.nav.k9.sak.typer.Periode
import no.nav.ung.kodeverk.Fagsystem as UngFagsystem
import no.nav.ung.kodeverk.behandling.BehandlingResultatType as UngBehandlingResultatType
import no.nav.ung.kodeverk.behandling.BehandlingStatus as UngBehandlingStatus
import no.nav.ung.kodeverk.behandling.BehandlingStegType as UngBehandlingStegType
import no.nav.ung.kodeverk.behandling.BehandlingType as UngBehandlingType
import no.nav.ung.kodeverk.behandling.BehandlingÅrsakType as UngBehandlingÅrsakType
import no.nav.ung.kodeverk.behandling.FagsakYtelseType as UngFagsakYtelseType
import no.nav.ung.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon as UngAksjonspunktDefinisjon
import no.nav.ung.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus as UngAksjonspunktStatus
import no.nav.ung.kodeverk.behandling.aksjonspunkt.Venteårsak as UngVenteårsak
import no.nav.ung.kodeverk.hendelse.EventHendelse as UngEventHendelse
import no.nav.ung.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto as UngAksjonspunktTilstandDto
import no.nav.ung.sak.typer.Periode as UngPeriode
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource
import kotlin.random.Random

object localSetup : KoinComponent {
    private val punsjEventHandler: K9PunsjEventHandler by inject()
    private val tilbakeEventHandler: K9TilbakeEventHandler by inject()
    private val sakEventHandler: K9SakEventHandler by inject()
    private val klageEventHandler: K9KlageEventHandler by inject()
    private val ungSakEventHandler: UngSakEventHandler by inject()
    private val eventTilOppgaveAdapter: EventTilOppgaveAdapter by inject()
    private val profile: KoinProfile by inject()
    private val dataSource: DataSource by inject()

    fun addSaksbehandler(saksbehandlerfelter: Map<String, Any>) {
        return using(sessionOf(dataSource)) {
            it.transaction { tx ->
                val saksbehandlerId = tx.run(
                    queryOf(
                        """
                        insert into saksbehandler (navident, navn, epost, enhet, skjermet)
                        values (:navident, :navn, :epost, :enhet, false)
                        on conflict (epost) do update
                            set navident = :navident,
                                navn = :navn,
                                epost = :epost,
                                enhet = :enhet,
                                skjermet = :skjermet
                        returning id
                     """,
                        saksbehandlerfelter
                    ).map { row -> row.long("id") }.asSingle
                )
                tx.run(
                    queryOf(
                        """
                        insert into saksbehandler_omrade(saksbehandler_id, omrade_id)
                        values (:saksbehandler_id, (select id from omrade where ekstern_id = :omrade))
                        on conflict do nothing
                     """,
                        mapOf("saksbehandler_id" to saksbehandlerId, "omrade" to Områder.K9.eksternId)
                    ).asExecute
                )
                tx.run(
                    queryOf(
                        """
                        insert into saksbehandler_omrade(saksbehandler_id, omrade_id)
                        values (:saksbehandler_id, (select id from omrade where ekstern_id = :omrade))
                        on conflict do nothing
                     """,
                        mapOf(
                            "saksbehandler_id" to saksbehandlerId,
                            "omrade" to Områder.AKTIVITETSPENGER.eksternId
                        )
                    ).asExecute
                )
            }
        }
    }

    fun initSaksbehandlere() {
        if (profile == KoinProfile.LOCAL) {
            runBlocking {
                listOf(
                    mapOf(
                        "navident" to "Z123456",
                        "navn" to "Saksbehandler Sara",
                        "epost" to "saksbehandler.sara@nav.no",
                        "enhet" to "3450",
                        "skjermet" to false,
                    ),
                    mapOf(
                        "navident" to "Z167457",
                        "navn" to "Saksbehandler Lars",
                        "epost" to "saksbehandler.lars@nav.no",
                        "enhet" to "3450",
                        "skjermet" to false,
                    ),
                    mapOf(
                        "navident" to "Z321457",
                        "navn" to "Saksbehandler Edgar",
                        "epost" to "saksbehandler.edgar@nav.no",
                        "enhet" to "3450",
                        "skjermet" to false,
                    )
                ).forEach { addSaksbehandler(it) }
            }
        }
    }

    fun initK9SakOppgaver(antall: Int) {
        if (profile == KoinProfile.LOCAL) {
            for (i in 0..<antall) {
                val eksternId = UUID.randomUUID()
                val behandlingId = Random.nextLong(0, 2000)
                val saksnummer = behandlingId.toString(36).uppercase().replace("O", "o").replace("I", "i")
                val ytelseTypeKode = listOf(
                    K9FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
                    K9FagsakYtelseType.PPN,
                    K9FagsakYtelseType.OLP,
                    K9FagsakYtelseType.OMSORGSPENGER_AO,
                ).shuffled().first().kode
                val opprettetBehandling = LocalDateTime.now().minusDays(Random.nextLong(10, 20))
                val aktørId = "2392173967319"
                val pleietrengendeAktørId = "1234567890123"

                val event = K9SakEventDto(
                    eksternId,
                    Fagsystem.K9SAK,
                    saksnummer,
                    fagsakPeriode = Periode(LocalDate.now().minusMonths(2), LocalDate.now()),
                    behandlingId = behandlingId,
                    fraEndringsdialog = false,
                    resultatType = BehandlingResultatType.IKKE_FASTSATT.kode,
                    behandlendeEnhet = null,
                    aksjonspunktTilstander = if (!Random.nextBoolean()) listOf(
                        AksjonspunktTilstandDto(
                            AksjonspunktKodeDefinisjon.VENT_PGA_FOR_TIDLIG_SØKNAD_KODE,
                            AksjonspunktStatus.OPPRETTET,
                            Venteårsak.AVV_IM_MOT_SØKNAD_AT,
                            null,
                            LocalDateTime.now(),
                            LocalDateTime.now(),
                            LocalDateTime.now(),
                        )
                    ) else emptyList(),
                    søknadsårsaker = mutableListOf<SøknadÅrsak>().map { it.kode },
                    behandlingsårsaker = BehandlingÅrsakType.entries.shuffled().subList(0, Random.nextInt(5))
                        .map { it.kode },
                    ansvarligSaksbehandlerIdent = null as String?,
                    ansvarligBeslutterForTotrinn = null as String?,
                    ansvarligSaksbehandlerForTotrinn = null as String?,
                    opprettetBehandling = opprettetBehandling,
                    vedtaksdato = null,
                    pleietrengendeAktørId = pleietrengendeAktørId,
                    aktørId = aktørId,
                    behandlingStatus = BehandlingStatus.UTREDES.kode,
                    behandlingSteg = BehandlingStegType.KONTROLLER_FAKTA.kode,
                    behandlingTypeKode = no.nav.k9.kodeverk.behandling.BehandlingType.FØRSTEGANGSSØKNAD.kode,
                    behandlingstidFrist = null,
                    eventHendelse = EventHendelse.AKSJONSPUNKT_OPPRETTET,
                    eventTid = LocalDateTime.now().minusSeconds((antall - i).toLong()),
                    aksjonspunktKoderMedStatusListe = mutableMapOf(),
                    ytelseTypeKode = ytelseTypeKode,
                    eldsteDatoMedEndringFraSøker = LocalDateTime.now(),
                    merknader = emptyList()
                )

                // Prosesser en event
                sakEventHandler.prosesser(event)
                if (Random.nextBoolean()) {
                    // Halvparten av gangene, prosesser to for samme pleietrengende
                    sakEventHandler.prosesser(event.copy(eksternId = UUID.randomUUID(), aktørId = "2392173967320"))
                }

                // ferdigstill noen av sakene
                if (Random.nextBoolean()) {
                    sakEventHandler.prosesser(
                        K9SakEventDto(
                            eksternId,
                            Fagsystem.K9SAK,
                            saksnummer,
                            behandlingId = behandlingId,
                            fraEndringsdialog = false,
                            resultatType = BehandlingResultatType.INNVILGET.kode,
                            aksjonspunktTilstander = emptyList(),
                            søknadsårsaker = mutableListOf<SøknadÅrsak>().map { it.kode },
                            behandlingsårsaker = mutableListOf<BehandlingÅrsakType>().map { it.kode },
                            ansvarligSaksbehandlerIdent = "Z123456",
                            ansvarligBeslutterForTotrinn = "Y123456",
                            ansvarligSaksbehandlerForTotrinn = "Z123456",
                            opprettetBehandling = LocalDateTime.now(),
                            vedtaksdato = LocalDate.now(),
                            pleietrengendeAktørId = pleietrengendeAktørId,
                            aktørId = aktørId,
                            behandlingStatus = BehandlingStatus.AVSLUTTET.kode,
                            behandlingSteg = BehandlingStegType.IVERKSETT_VEDTAK.kode,
                            behandlingTypeKode = no.nav.k9.kodeverk.behandling.BehandlingType.FØRSTEGANGSSØKNAD.kode,
                            behandlingstidFrist = null,
                            eventHendelse = EventHendelse.AKSJONSPUNKT_UTFØRT,
                            eventTid = LocalDateTime.now().minusSeconds((antall - i).toLong()),
                            aksjonspunktKoderMedStatusListe = mutableMapOf(),
                            ytelseTypeKode = ytelseTypeKode,
                            eldsteDatoMedEndringFraSøker = LocalDateTime.now(),
                            merknader = emptyList()
                        )
                    )
                }
            }
        }
    }

    fun initKlageoppgaver(antall: Int) {
        if (profile == KoinProfile.LOCAL) {
            for (i in 0..<antall) {
                val event = K9KlageEventDto(
                    eksternId = UUID.randomUUID(),
                    saksnummer = Random.nextInt(0, 200 * antall).toString(),
                    resultatType = null,
                    behandlendeEnhet = null,
                    opprettetBehandling = LocalDateTime.now(),
                    aktørId = Random.nextLong(1_000_000_000_000, 9_000_000_000_000).toString(),
                    behandlingStatus = BehandlingStatus.UTREDES.kode,
                    behandlingSteg = BehandlingStegType.FATTE_VEDTAK.kode,
                    behandlingTypeKode = "BT-003",
                    behandlingstidFrist = null,
                    eventHendelse = no.nav.k9.klage.kodeverk.behandling.oppgavetillos.EventHendelse.AKSJONSPUNKT_OPPRETTET,
                    eventTid = LocalDateTime.now().minusSeconds((antall - i).toLong()),
                    påklagdBehandlingId = null,
                    påklagdBehandlingType = null,
                    fagsystem = no.nav.k9.klage.kodeverk.Fagsystem.K9SAK,
                    utenlandstilsnitt = false,
                    ansvarligBeslutter = "",
                    ansvarligSaksbehandler = "",
                    ytelseTypeKode = "PSB",
                    fagsakPeriode = null,
                    pleietrengendeAktørId = null,
                    relatertPartAktørId = null,
                    aksjonspunkttilstander = listOf(
                        Aksjonspunkttilstand(
                            "7100",
                            no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus.OPPRETTET,
                            no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.Venteårsak.OVERSENDT_KABAL,
                            LocalDateTime.now(),
                            LocalDateTime.now(),
                            LocalDateTime.now()
                        )
                    ),
                    vedtaksdato = null,
                    behandlingsårsaker = null,
                )
                klageEventHandler.prosesser(event)
            }

        }
    }

    fun initTilbakeoppgaver(antall: Int) {
        if (profile == KoinProfile.LOCAL) {
            for (i in 0..<antall) {
                val event = K9TilbakeEventDto(
                    eksternId = UUID.randomUUID(),
                    saksnummer = Random.nextInt(0, 200 * antall).toString(),
                    behandlingId = 123L,
                    resultatType = null,
                    behandlendeEnhet = null,
                    ansvarligSaksbehandlerIdent = null,
                    opprettetBehandling = LocalDateTime.now(),
                    aktørId = Random.nextLong(1_000_000_000_000, 9_000_000_000_000).toString(),
                    behandlingStatus = BehandlingStatus.UTREDES.kode,
                    behandlingSteg = BehandlingStegType.FATTE_VEDTAK.kode,
                    behandlingTypeKode = "BT-007",
                    behandlingstidFrist = null,
                    eventHendelse = EventHendelse.AKSJONSPUNKT_OPPRETTET,
                    eventTid = LocalDateTime.now().minusSeconds((antall - i).toLong()),
                    aksjonspunktKoderMedStatusListe = mutableMapOf(AksjonspunktDefinisjonK9Tilbake.VURDER_TILBAKEKREVING.kode to AksjonspunktStatus.OPPRETTET.kode),
                    ytelseTypeKode = K9FagsakYtelseType.PLEIEPENGER_SYKT_BARN.kode,
                    ansvarligBeslutterIdent = null,
                    førsteFeilutbetaling = LocalDate.now().minusDays(Random.nextLong(100)).toString(),
                    feilutbetaltBeløp = Random.nextLong(1000, 20000),
                    href = null,
                    fagsystem = Fagsystem.K9TILBAKE.kode,
                )
                tilbakeEventHandler.prosesser(event)
            }
        }
    }

    fun initPunsjoppgaver(antall: Int) {
        if (profile == KoinProfile.LOCAL) {
            for (i in 0..<antall) {
                punsjEventHandler.prosesser(
                    K9PunsjEventDto(
                        eksternId = UUID.randomUUID(),
                        journalpostId = JournalpostId(Random.nextLong(100000000, 999999999).toString()),
                        eventTid = LocalDateTime.now(),
                        status = Oppgavestatus.AAPEN,
                        aktørId = AktørId(Random.nextLong(1_000_000_000_000, 1_000_000_000_002).toString()),
                        aksjonspunktKoderMedStatusListe = mutableMapOf("PUNSJ" to "OPPR"),
                        pleietrengendeAktørId = null,
                        type = BehandlingType.entries.filter { it.kodeverk == "PUNSJ_INNSENDING_TYPE" }.shuffled()
                            .first().kode,
                        ytelse = K9FagsakYtelseType.entries.filter { it != K9FagsakYtelseType.UNGDOMSYTELSE && it != K9FagsakYtelseType.OMSORGSDAGER }
                            .shuffled().first().kode,
                        sendtInn = null,
                        ferdigstiltAv = null,
                        journalførtTidspunkt = listOf(LocalDateTime.now(), null).shuffled().first(),
                    )
                )
            }
        }
    }

    private val lokalkontorAksjonspunkter = listOf(
        UngAksjonspunktDefinisjon.LOKALKONTOR_FORESLÅR_VILKÅR,
        UngAksjonspunktDefinisjon.LOKALKONTOR_BESLUTTER_VILKÅR,
        UngAksjonspunktDefinisjon.VURDER_FAKTA_OM_BOSTED,
        UngAksjonspunktDefinisjon.VURDER_BOSTEDVILKÅR,
        UngAksjonspunktDefinisjon.VURDER_BISTANDSVILKÅR,
    )

    private val navSentraltAksjonspunkter = listOf(
        UngAksjonspunktDefinisjon.KONTROLLER_INNTEKT,
        UngAksjonspunktDefinisjon.KONTROLLER_OPPLYSNINGER_OM_SØKNADSFRIST,
        UngAksjonspunktDefinisjon.FORESLÅ_VEDTAK,
        UngAksjonspunktDefinisjon.FATTER_VEDTAK,
        UngAksjonspunktDefinisjon.AUTO_SATT_PÅ_VENT_RAPPORTERINGSFRIST,
    )

    fun initAktivitetspengeroppgaver(antall: Int) {
        if (profile != KoinProfile.LOCAL) {
            return
        }
        for (i in 0..<antall) {
            val eksternId = UUID.randomUUID()
            val saksnummer = "AKT" + Random.nextInt(100_000, 999_999)
            val aktørId = Random.nextLong(1_000_000_000_000, 9_000_000_000_000).toString()
            val opprettetBehandling = LocalDateTime.now().minusDays(Random.nextLong(3, 40))
            val eventTid = LocalDateTime.now().minusSeconds((antall - i).toLong())
            val behandlingType = listOf(
                UngBehandlingType.FØRSTEGANGSSØKNAD,
                UngBehandlingType.REVURDERING,
            ).shuffled().first()

            val hosLokalkontor = Random.nextBoolean()
            val aksjonspunkt = if (hosLokalkontor) {
                lokalkontorAksjonspunkter.shuffled().first()
            } else {
                navSentraltAksjonspunkter.shuffled().first()
            }

            val aksjonspunktTilstand = UngAksjonspunktTilstandDto(
                aksjonspunkt.kode,
                UngAksjonspunktStatus.OPPRETTET,
                if (aksjonspunkt.erAutopunkt()) UngVenteårsak.VENT_INNTEKT_RAPPORTERINGSFRIST else UngVenteårsak.UDEFINERT,
                null,
                if (aksjonspunkt.erAutopunkt()) LocalDateTime.now().plusDays(14) else null,
                opprettetBehandling,
                opprettetBehandling,
            )

            val åpentEvent = UngSakEventDto(
                eksternId = eksternId,
                fagsystem = UngFagsystem.UNG_SAK,
                saksnummer = saksnummer,
                aktørId = aktørId,
                eventTid = eventTid,
                eventHendelse = UngEventHendelse.AKSJONSPUNKT_OPPRETTET,
                behandlingStatus = UngBehandlingStatus.UTREDES.kode,
                behandlingSteg = aksjonspunkt.behandlingSteg.kode,
                behandlendeEnhet = AktBehandlendeEnhet.entries
                    .filter { it != AktBehandlendeEnhet.UKJENT }
                    .shuffled().first().kode,
                ansvarligBeslutterForTotrinn = null,
                ansvarligSaksbehandlerForTotrinn = null,
                navKontorAnsvarligSaksbehandler = if (hosLokalkontor) "Z123456" else null,
                navKontorBeslutter = null,
                resultatType = UngBehandlingResultatType.IKKE_FASTSATT.kode,
                ytelseTypeKode = UngFagsakYtelseType.AKTIVITETSPENGER.kode,
                behandlingTypeKode = behandlingType.kode,
                eldsteDatoMedEndringFraSøker = opprettetBehandling,
                opprettetBehandling = opprettetBehandling,
                fagsakPeriode = UngPeriode(LocalDate.now().minusMonths(3), LocalDate.now()),
                aksjonspunktTilstander = listOf(aksjonspunktTilstand),
                nyeKrav = Random.nextBoolean(),
                vedtaksdato = null,
                behandlingstidFrist = LocalDate.now().plusWeeks(3),
                behandlingsårsaker = if (behandlingType == UngBehandlingType.REVURDERING) {
                    listOf(UngBehandlingÅrsakType.RE_ANNET.kode)
                } else {
                    emptyList()
                },
            )
            prosesserUngSakEvent(åpentEvent)

            // Ferdigstill noen av behandlingene. Kun for nav-sentrale aksjonspunkt, slik at
            // oppgavetypen (del1/del2) ikke endrer seg mellom versjonene av samme oppgave.
            if (!hosLokalkontor && Random.nextBoolean()) {
                prosesserUngSakEvent(
                    åpentEvent.copy(
                        eventTid = eventTid.plusSeconds(1),
                        eventHendelse = UngEventHendelse.AKSJONSPUNKT_UTFØRT,
                        behandlingStatus = UngBehandlingStatus.AVSLUTTET.kode,
                        behandlingSteg = UngBehandlingStegType.IVERKSETT_VEDTAK.kode,
                        ansvarligSaksbehandlerForTotrinn = "Z123456",
                        ansvarligBeslutterForTotrinn = "Z167457",
                        resultatType = UngBehandlingResultatType.INNVILGET.kode,
                        aksjonspunktTilstander = listOf(
                            UngAksjonspunktTilstandDto(
                                aksjonspunkt.kode,
                                UngAksjonspunktStatus.UTFØRT,
                                UngVenteårsak.UDEFINERT,
                                "Z123456",
                                null,
                                opprettetBehandling,
                                LocalDateTime.now(),
                            )
                        ),
                        vedtaksdato = LocalDate.now(),
                    )
                )
            }
        }
    }

    /**
     * UngSakEventHandler lagrer kun eventet, så oppgaven må mappes eksplisitt her
     * i stedet for å vente på oppgavevaktmesteren.
     */
    private fun prosesserUngSakEvent(event: UngSakEventDto) {
        ungSakEventHandler.prosesser(
            eksternId = event.eksternId.toString(),
            eksternVersjon = event.eventTid.toString(),
            event = LosObjectMapper.instance.writeValueAsString(event),
        )
        eventTilOppgaveAdapter.oppdaterOppgaveForEksternId(
            EventNøkkel(Fagsystem.UNGSAK, event.eksternId.toString())
        )
    }
}
