package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.punsjtillos

import assertk.assertThat
import assertk.assertions.isNull
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.OppgaveTestDataBuilder
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.PunsjEventDtoBuilder
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.TestSaksbehandler
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventHandler
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveKoTjeneste
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.AktivOppgaveRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.db.EksternOppgaveId
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonApisTjeneste
import no.nav.k9.sak.typer.AktørId
import no.nav.k9.sak.typer.JournalpostId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDateTime
import java.util.*


class K9PunsjTilLosIT : AbstractK9LosIntegrationTest() {

    lateinit var eventHandler: K9PunsjEventHandler
    lateinit var oppgaveKøTjeneste: OppgaveKoTjeneste

    lateinit var reservasjonApisTjeneste: ReservasjonApisTjeneste
    lateinit var transactionalManager: TransactionalManager
    lateinit var aktivOppgaveRepository: AktivOppgaveRepository

    @BeforeEach
    fun setup() {
        eventHandler = get<K9PunsjEventHandler>()
        oppgaveKøTjeneste = get<OppgaveKoTjeneste>()
        reservasjonApisTjeneste = get<ReservasjonApisTjeneste>()
        transactionalManager = get<TransactionalManager>()
        aktivOppgaveRepository = get<AktivOppgaveRepository>()

        TestSaksbehandler().init()
        OppgaveTestDataBuilder()
    }

    @Test
    fun `Punsjadapter skal håndtere eventer fra punsj som enda ikke er klassifisert til noen ytelse`() {
        val punsjEventDtoBuilder = PunsjEventDtoBuilder(ytelse = FagsakYtelseType.UKJENT)
        eventHandler.prosesser(punsjEventDtoBuilder.papirsøknad().build())

        // Sjekke at de dukker opp i oppgavequery
    }

    @Test
    fun `Skal ta i mott eventer og lukke oppgave når siste event er LUKKET`() {
        var nå = LocalDateTime.now()
        var punsjId = UUID.randomUUID()
        var søkerAktørId = AktørId(2000000000000)
        var pleietrengendeAktørId = AktørId(2000000000001)
        val event1 = PunsjEventDto(
            type = "PAPIRSØKNAD",
            status = Oppgavestatus.AAPEN,
            ytelse = "PSB",
            aktørId = søkerAktørId,
            eventTid = nå.minusMinutes(2),
            eksternId = punsjId,
            journalpostId = JournalpostId(1),
            aksjonspunktKoderMedStatusListe = mutableMapOf("PUNSJ" to "OPPR"),
        )
        val event2 = PunsjEventDto(
            type = "PAPIRSØKNAD",
            status = Oppgavestatus.AAPEN,
            ytelse = "PSB",
            aktørId = søkerAktørId,
            eventTid = nå.minusMinutes(1),
            eksternId = punsjId,
            journalpostId = JournalpostId(1),
            journalførtTidspunkt = nå.minusMinutes(1),
            pleietrengendeAktørId = pleietrengendeAktørId.aktørId.toString(),
            aksjonspunktKoderMedStatusListe = mutableMapOf("PUNSJ" to "OPPR"),
        )

        val event3 = PunsjEventDto(
            type = "PAPIRSØKNAD",
            status = Oppgavestatus.LUKKET,
            ytelse = null,
            aktørId = søkerAktørId,
            eventTid = nå,
            sendtInn = false,
            eksternId = punsjId,
            journalpostId = JournalpostId(1),
            aksjonspunktKoderMedStatusListe = mutableMapOf("PUNSJ" to "OPPR"),
        )

        eventHandler.prosesser(event1)
        eventHandler.prosesser(event2)
        eventHandler.prosesser(event3)

        val aktivOppgave = transactionalManager.transaction { tx -> aktivOppgaveRepository.hentOppgaveForEksternId(tx, EksternOppgaveId("K9", punsjId.toString())) }
        assertThat(aktivOppgave).isNull() //når oppgaven lukkes fjernes den også fra aktiv-tabellene

    }
}