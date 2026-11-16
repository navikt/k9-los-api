package no.nav.k9.los.oppgavedefinisjon.oppgavetype

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.FeltType
import no.nav.k9.los.OppgaveTestDataBuilder
import no.nav.k9.los.domeneadaptere.eventlager.EventNøkkel
import no.nav.k9.los.domeneadaptere.eventlager.EventRepository
import no.nav.k9.los.domeneadaptere.eventlager.Fagsystem
import no.nav.k9.los.domeneadaptere.eventmottak.ung.sak.UngSakEventDto
import no.nav.k9.los.domeneadaptere.eventtiloppgave.EventTilOppgaveAdapter
import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.AvpOmrådesetup
import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.kodeverk.AvpBehandlendeEnhet
import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.kodeverk.AvpOppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.oppgavedefinisjon.AktivitetspengerFelter
import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.oppgavedefinisjon.AktivitetspengerOppgavetyper
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.kodeverk.K9Oppgavetypenavn
import no.nav.k9.los.domeneadaptere.statistikk.StatistikkRepository
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavemottak.OppgaveV3Repository
import no.nav.ung.kodeverk.Fagsystem as UngFagsystem
import no.nav.ung.kodeverk.behandling.BehandlingResultatType
import no.nav.ung.kodeverk.behandling.BehandlingStatus
import no.nav.ung.kodeverk.behandling.BehandlingType
import no.nav.ung.kodeverk.behandling.FagsakYtelseType
import no.nav.ung.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.ung.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.ung.kodeverk.behandling.aksjonspunkt.Venteårsak
import no.nav.ung.kodeverk.hendelse.EventHendelse
import no.nav.ung.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto
import no.nav.ung.sak.typer.Periode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class PåkrevdFeltHistorikkvaskTest : AbstractK9LosIntegrationTest() {

    private val eksternId = UUID.randomUUID()
    private val opprettetBehandling = LocalDateTime.of(2026, 3, 1, 8, 0)
    private val avpOppgavetype = AvpOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL1

    private lateinit var eventRepository: EventRepository
    private lateinit var transactionalManager: TransactionalManager
    private lateinit var adapter: EventTilOppgaveAdapter
    private lateinit var oppgavetypeTjeneste: OppgavetypeTjeneste
    private lateinit var statistikkRepository: StatistikkRepository

    @BeforeEach
    fun setup() {
        get<AvpOmrådesetup>().setup()
        eventRepository = get()
        transactionalManager = get()
        adapter = get()
        oppgavetypeTjeneste = get()
        statistikkRepository = get()
    }

    @Test
    fun `felt som blir påkrevd uten defaultverdi bestiller historikkvask for oppgaver som mangler verdien`() {
        prosesserOppgave()

        oppgavetypeTjeneste.oppdater(oppgavetyperMedPåkrevdFelt(AktivitetspengerFelter.Saksbehandling.UTENLANDSSAK.id))

        assertThat(eventRepository.hentAntallHistorikkvaskbestillinger()).isEqualTo(1L)
    }

    @Test
    fun `felt som blir påkrevd bestiller ikke vask når alle versjoner har verdien`() {
        prosesserOppgave()

        oppgavetypeTjeneste.oppdater(oppgavetyperMedPåkrevdFelt(AktivitetspengerFelter.Sak.MOTTATT_DATO.id))

        assertThat(eventRepository.hentAntallHistorikkvaskbestillinger()).isEqualTo(0L)
    }

    @Test
    fun `felt som blir påkrevd bestiller bare vask for samme fagsystem og ekstern id`() {
        verifiserHistorikkvaskbestilling(nyttFelt = false)
    }

    @Test
    fun `nytt påkrevd felt bestiller bare vask for samme fagsystem og ekstern id`() {
        verifiserHistorikkvaskbestilling(nyttFelt = true)
    }

    @Test
    fun `k9 oppgavetyper bestiller bare vask for eget fagsystem`() {
        val forventedeBestillinger = mutableSetOf<Pair<Fagsystem, String>>()
        mapOf(
            "k9sak" to Fagsystem.K9SAK,
            "k9klage" to Fagsystem.K9KLAGE,
            "k9tilbake" to Fagsystem.K9TILBAKE,
            "k9punsj" to Fagsystem.PUNSJ
        ).forEach { (oppgavetypeId, fagsystem) ->
            assertThat(K9Oppgavetypenavn.fraFagsystem(fagsystem).kode).isEqualTo(oppgavetypeId)
            val oppgave = OppgaveTestDataBuilder(oppgavetypeId).lagOgLagre()
            val felt = oppgave.oppgavetype.oppgavefelter.first { !it.påkrevd && it.defaultverdi == null }
            val påkrevdFelt = Oppgavefelt(
                id = felt.id,
                feltDefinisjon = felt.feltDefinisjon,
                visPåOppgave = felt.visPåOppgave,
                påkrevd = true,
                defaultverdi = null,
                feltutleder = felt.feltutleder
            )
            transactionalManager.transaction { tx ->
                Fagsystem.entries.forEach {
                    eventRepository.upsertOgLåsEventnøkkel(it, oppgave.eksternId, tx)
                }
                get<OppgavetypeRepository>().endre(
                    OppgavetypeEndring(
                        oppgavetype = oppgave.oppgavetype,
                        felterSomSkalLeggesTil = emptyList(),
                        felterSomSkalFjernes = emptyList(),
                        felterSomSkalEndresMedNyeVerdier = listOf(OppgavefeltDelta(felt, påkrevdFelt))
                    ),
                    tx
                )
            }
            forventedeBestillinger.add(fagsystem to oppgave.eksternId)
            assertThat(eventRepository.hentAlleHistorikkvaskbestillinger().map { it.fagsystem to it.eksternId }.toSet())
                .isEqualTo(forventedeBestillinger)
        }
    }

    private fun verifiserHistorikkvaskbestilling(nyttFelt: Boolean) {
        prosesserOppgave()
        val feltId = AktivitetspengerFelter.Saksbehandling.UTENLANDSSAK.id
        val oppgavetyper = oppgavetyperMedPåkrevdFelt(feltId)
        if (nyttFelt) {
            oppgavetypeTjeneste.oppdater(
                oppgavetyper.copy(
                    oppgavetyper = oppgavetyper.oppgavetyper.map { oppgavetype ->
                        oppgavetype.copy(
                            oppgavefelter = oppgavetype.oppgavefelter.filterNot { it.id == feltId }.toSet()
                        )
                    }.toSet()
                )
            )
        }
        transactionalManager.transaction { tx ->
            Fagsystem.entries.forEach { fagsystem ->
                eventRepository.upsertOgLåsEventnøkkel(fagsystem, eksternId.toString(), tx)
            }
            eventRepository.upsertOgLåsEventnøkkel(Fagsystem.UNGSAK, UUID.randomUUID().toString(), tx)
        }

        oppgavetypeTjeneste.oppdater(oppgavetyper)

        assertThat(eventRepository.hentAlleHistorikkvaskbestillinger().map { it.fagsystem to it.eksternId })
            .isEqualTo(listOf(Fagsystem.UNGSAK to eksternId.toString()))
    }

    @Test
    fun `dvh-sending holder tilbake oppgaver med åpen historikkvaskbestilling`() {
        prosesserOppgave()
        assertThat(statistikkRepository.hentOppgaverSomIkkeErSendt(avpOppgavetype)).isNotEmpty()

        eventRepository.bestillHistorikkvask(Fagsystem.UNGSAK, eksternId.toString())
        assertThat(statistikkRepository.hentOppgaverSomIkkeErSendt(avpOppgavetype)).isEmpty()

        transactionalManager.transaction { tx ->
            eventRepository.settHistorikkvaskFerdig(Fagsystem.UNGSAK, eksternId.toString(), tx)
        }
        assertThat(statistikkRepository.hentOppgaverSomIkkeErSendt(avpOppgavetype)).isNotEmpty()
    }

    @Test
    fun `dvh-sending holder bare tilbake k9sak fra samme fagsystem og ekstern id`() {
        verifiserHistorikkvaskAvgrensning(Fagsystem.K9SAK)
    }

    @Test
    fun `dvh-sending holder bare tilbake k9klage fra samme fagsystem og ekstern id`() {
        verifiserHistorikkvaskAvgrensning(Fagsystem.K9KLAGE)
    }

    @Test
    fun `dvh-sending skiller mellom k9sak og k9klage med samme ekstern id`() {
        val oppgaveIder = mapOf(K9Oppgavetypenavn.SAK to Fagsystem.K9SAK, K9Oppgavetypenavn.KLAGE to Fagsystem.K9KLAGE)
            .map { (oppgavetype, fagsystem) ->
                val oppgave = OppgaveTestDataBuilder(oppgavetype.kode)
                    .medOppgaveFeltVerdi(FeltType.BEHANDLINGUUID, eksternId.toString())
                    .lagOgLagre()
                val id = transactionalManager.transaction { tx ->
                    statistikkRepository.bestillDvhSending(
                        Områder.K9, oppgave.eksternId, oppgave.eksternVersjon, oppgavetype.kode, tx
                    )
                    eventRepository.upsertOgLåsEventnøkkel(fagsystem, oppgave.eksternId, tx)
                    get<OppgaveV3Repository>().hentOppgaveIdStatusOgHøyesteInternversjon(
                        tx, oppgave.eksternId, oppgavetype.kode, Områder.K9
                    ).first!!.id
                }
                fagsystem to id
            }.toMap()
        val sakId = oppgaveIder.getValue(Fagsystem.K9SAK)
        val klageId = oppgaveIder.getValue(Fagsystem.K9KLAGE)
        assertThat(statistikkRepository.hentOppgaverSomIkkeErSendt(K9Oppgavetypenavn.SAK)).isEqualTo(listOf(sakId))
        assertThat(statistikkRepository.hentOppgaverSomIkkeErSendt(K9Oppgavetypenavn.KLAGE)).isEqualTo(listOf(klageId))

        eventRepository.bestillHistorikkvask(Fagsystem.K9SAK, eksternId.toString())
        assertThat(statistikkRepository.hentOppgaverSomIkkeErSendt(K9Oppgavetypenavn.SAK)).isEmpty()
        assertThat(statistikkRepository.hentOppgaverSomIkkeErSendt(K9Oppgavetypenavn.KLAGE)).isEqualTo(listOf(klageId))

        eventRepository.bestillHistorikkvask(Fagsystem.K9KLAGE, eksternId.toString())
        assertThat(statistikkRepository.hentOppgaverSomIkkeErSendt(K9Oppgavetypenavn.SAK)).isEmpty()
        assertThat(statistikkRepository.hentOppgaverSomIkkeErSendt(K9Oppgavetypenavn.KLAGE)).isEmpty()

        transactionalManager.transaction { tx ->
            eventRepository.settHistorikkvaskFerdig(Fagsystem.K9SAK, eksternId.toString(), tx)
        }
        assertThat(statistikkRepository.hentOppgaverSomIkkeErSendt(K9Oppgavetypenavn.SAK)).isEqualTo(listOf(sakId))
        assertThat(statistikkRepository.hentOppgaverSomIkkeErSendt(K9Oppgavetypenavn.KLAGE)).isEmpty()

        transactionalManager.transaction { tx ->
            eventRepository.settHistorikkvaskFerdig(Fagsystem.K9KLAGE, eksternId.toString(), tx)
        }
        assertThat(statistikkRepository.hentOppgaverSomIkkeErSendt(K9Oppgavetypenavn.SAK)).isEqualTo(listOf(sakId))
        assertThat(statistikkRepository.hentOppgaverSomIkkeErSendt(K9Oppgavetypenavn.KLAGE)).isEqualTo(listOf(klageId))
    }

    @Test
    fun `dvh-bestilling og gjenbestilling bruker kodene fra typesikre oppgavetyper`() {
        val oppgavetyper = listOf(K9Oppgavetypenavn.SAK, K9Oppgavetypenavn.KLAGE)
        val eksternIder = oppgavetyper.map { OppgaveTestDataBuilder(it.kode).lagOgLagre().eksternId }

        statistikkRepository.bestillDvhSending(K9Oppgavetypenavn.SAK.kode)
        assertThat(statistikkRepository.hentOppgaverSomIkkeErSendt(K9Oppgavetypenavn.SAK).size).isEqualTo(1)
        assertThat(statistikkRepository.hentOppgaverSomIkkeErSendt(K9Oppgavetypenavn.KLAGE)).isEmpty()

        statistikkRepository.bestillDvhSending()
        val forventedeOppgaveIder = oppgavetyper.associateWith {
            statistikkRepository.hentOppgaverSomIkkeErSendt(it).single()
        }
        forventedeOppgaveIder.values.forEach { statistikkRepository.kvitterSending(it) }

        transactionalManager.transaction { tx ->
            statistikkRepository.bestillDvhSendingForEksternIder(eksternIder, tx)
        }
        forventedeOppgaveIder.forEach { (oppgavetype, id) ->
            assertThat(statistikkRepository.hentOppgaverSomIkkeErSendt(oppgavetype)).isEqualTo(listOf(id))
        }
        forventedeOppgaveIder.values.forEach { statistikkRepository.kvitterSending(it) }

        assertThat(statistikkRepository.populerPendingFraOppgaveV3()).isEqualTo(2)
        forventedeOppgaveIder.forEach { (oppgavetype, id) ->
            assertThat(statistikkRepository.hentOppgaverSomIkkeErSendt(oppgavetype)).isEqualTo(listOf(id))
        }
    }

    private fun verifiserHistorikkvaskAvgrensning(fagsystem: Fagsystem) {
        prosesserOppgave()
        val oppgavetype = K9Oppgavetypenavn.fraFagsystem(fagsystem)
        val oppgave = OppgaveTestDataBuilder(oppgavetype.kode)
            .medOppgaveFeltVerdi(FeltType.BEHANDLINGUUID, eksternId.toString())
            .lagOgLagre()
        transactionalManager.transaction { tx ->
            statistikkRepository.bestillDvhSending(
                Områder.K9, oppgave.eksternId, oppgave.eksternVersjon, oppgavetype.kode, tx
            )
            eventRepository.upsertOgLåsEventnøkkel(fagsystem, oppgave.eksternId, tx)
        }
        val k9Pending = statistikkRepository.hentOppgaverSomIkkeErSendt(oppgavetype)
        val avpPending = statistikkRepository.hentOppgaverSomIkkeErSendt(avpOppgavetype)
        assertThat(k9Pending.size).isEqualTo(1)
        assertThat(avpPending).isNotEmpty()

        eventRepository.bestillHistorikkvask(Fagsystem.UNGSAK, eksternId.toString())
        assertThat(statistikkRepository.hentOppgaverSomIkkeErSendt(oppgavetype)).isEqualTo(k9Pending)
        assertThat(statistikkRepository.hentOppgaverSomIkkeErSendt(avpOppgavetype)).isEmpty()

        transactionalManager.transaction { tx ->
            eventRepository.settHistorikkvaskFerdig(Fagsystem.UNGSAK, eksternId.toString(), tx)
        }
        eventRepository.bestillHistorikkvask(fagsystem, eksternId.toString())
        assertThat(statistikkRepository.hentOppgaverSomIkkeErSendt(oppgavetype)).isEmpty()
        assertThat(statistikkRepository.hentOppgaverSomIkkeErSendt(avpOppgavetype)).isEqualTo(avpPending)

        transactionalManager.transaction { tx ->
            eventRepository.settHistorikkvaskFerdig(fagsystem, eksternId.toString(), tx)
            val annenEksternId = UUID.randomUUID().toString()
            eventRepository.upsertOgLåsEventnøkkel(fagsystem, annenEksternId, tx)
            eventRepository.bestillHistorikkvask(fagsystem, annenEksternId, tx)
        }
        assertThat(statistikkRepository.hentOppgaverSomIkkeErSendt(oppgavetype)).isEqualTo(k9Pending)
        assertThat(statistikkRepository.hentOppgaverSomIkkeErSendt(avpOppgavetype)).isEqualTo(avpPending)
    }

    private fun prosesserOppgave() {
        val event = lagEvent()
        transactionalManager.transaction { tx ->
            eventRepository.lagre(
                Fagsystem.UNGSAK,
                eksternId.toString(),
                event.eventTid.toString(),
                LosObjectMapper.instance.writeValueAsString(event),
                tx
            )
        }
        adapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.UNGSAK, eksternId.toString()))
    }

    private fun oppgavetyperMedPåkrevdFelt(feltId: String): OppgavetyperDto {
        val dto = AktivitetspengerOppgavetyper.tilDto("http://localhost")
        return dto.copy(
            oppgavetyper = dto.oppgavetyper.map { oppgavetype ->
                oppgavetype.copy(
                    oppgavefelter = oppgavetype.oppgavefelter.map { felt ->
                        if (felt.id == feltId) felt.copy(påkrevd = true) else felt
                    }.toSet()
                )
            }.toSet()
        )
    }

    private fun lagEvent(): UngSakEventDto {
        val aksjonspunkt = AksjonspunktDefinisjon.NAV_KONTOR_FORESLÅR_VILKÅR
        return UngSakEventDto(
            eksternId = eksternId,
            fagsystem = UngFagsystem.UNG_SAK,
            saksnummer = "AKT123456",
            aktørId = "1234567890123",
            eventTid = opprettetBehandling.plusHours(1),
            eventHendelse = EventHendelse.AKSJONSPUNKT_OPPRETTET,
            behandlingStatus = BehandlingStatus.UTREDES.kode,
            behandlingSteg = aksjonspunkt.behandlingSteg.kode,
            behandlendeEnhet = AvpBehandlendeEnhet.STYRINGSENHET.kode,
            navKontorbehandlendeEnhet = AvpBehandlendeEnhet.FALKENBORG.kode,
            ansvarligBeslutterForTotrinn = null,
            ansvarligSaksbehandlerForTotrinn = null,
            navKontorAnsvarligSaksbehandler = "Z123456",
            navKontorBeslutter = null,
            resultatType = BehandlingResultatType.IKKE_FASTSATT.kode,
            ytelseTypeKode = FagsakYtelseType.AKTIVITETSPENGER.kode,
            behandlingTypeKode = BehandlingType.FØRSTEGANGSSØKNAD.kode,
            eldsteDatoMedEndringFraSøker = opprettetBehandling,
            opprettetBehandling = opprettetBehandling,
            fagsakPeriode = Periode(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1)),
            aksjonspunktTilstander = listOf(
                AksjonspunktTilstandDto(
                    aksjonspunkt.kode,
                    AksjonspunktStatus.OPPRETTET,
                    Venteårsak.UDEFINERT,
                    null,
                    null,
                    opprettetBehandling,
                    opprettetBehandling,
                )
            ),
            nyeKrav = false,
            vedtaksdato = null,
            behandlingstidFrist = LocalDate.of(2026, 4, 1),
            behandlingsårsaker = emptyList(),
        )
    }
}
