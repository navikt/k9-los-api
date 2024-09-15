package no.nav.k9.los.nyoppgavestyring

import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.klagetillos.K9KlageTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.punsjtillos.K9PunsjTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.tilbaketillos.K9TilbakeTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveFeltverdi
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Repository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.*
import org.koin.test.KoinTest
import org.koin.test.get
import java.time.LocalDateTime
import java.util.*

class OppgaveTestDataBuilder(
    val definisjonskilde: String = "k9-sak-til-los",
    val oppgaveTypeNavn: String = "k9sak"
) : KoinTest {
    private var område: Område
    val områdeRepository = get<OmrådeRepository>()
    val områdeSetup = get<OmrådeSetup>()
    val transactionManager = get<TransactionalManager>()
    val oppgavetypeRepo = get<OppgavetypeRepository>()
    val oppgaverepo = get<OppgaveV3Repository>()
    val k9SakTilLosAdapterTjeneste = get<K9SakTilLosAdapterTjeneste>()
    val k9PunsjTilLosAdapterTjeneste = get<K9PunsjTilLosAdapterTjeneste>()
    val k9TilbakeTilLosAdapterTjeneste = get<K9TilbakeTilLosAdapterTjeneste>()
    val k9KlageTilLosAdapterTjeneste = get<K9KlageTilLosAdapterTjeneste>()

    var eksternVersjonTeller = 1000000

    init {
        områdeSetup.setup()
        område = områdeRepository.hent("K9")!!
        k9SakTilLosAdapterTjeneste.setup()
        k9PunsjTilLosAdapterTjeneste.setup()
        k9KlageTilLosAdapterTjeneste.setup()
        k9TilbakeTilLosAdapterTjeneste.setup()
    }

    val oppgaveFeltverdier = mutableSetOf<OppgaveFeltverdi>()

    val oppgavetype = transactionManager.transaction { tx ->
        val oppgavetype = oppgavetypeRepo.hent(område, definisjonskilde, tx)
        oppgavetype.oppgavetyper
            .firstOrNull { it.eksternId == oppgaveTypeNavn }
            ?: throw IllegalStateException("Fant ikke oppgavetype for $oppgaveTypeNavn i db")
    }

    fun medOppgaveFeltVerdi(feltTypeKode: FeltType, verdi: String): OppgaveTestDataBuilder {
        val oppgavefelter = oppgavetype.oppgavefelter
            .firstOrNull { it.feltDefinisjon.eksternId == feltTypeKode.eksternId }
            ?: throw IllegalStateException("Fant ikke ønsket feltdefinisjon i db")

        oppgaveFeltverdier.add(
            OppgaveFeltverdi(null, oppgavefelter, verdi)
        )
        return this
    }


    fun lagOgLagre(status: Oppgavestatus = Oppgavestatus.AAPEN): OppgaveV3 {
        return transactionManager.transaction { tx ->
            val oppgave = lag(status)
            oppgaverepo.nyOppgaveversjon(oppgave, tx)
            oppgave
        }
    }

    fun lag(status: Oppgavestatus = Oppgavestatus.AAPEN, reservasjonsnøkkel: String = "", eksternVersjon: String? = null): OppgaveV3 {
        return OppgaveV3(
            eksternId = oppgaveFeltverdier.firstOrNull {
                it.oppgavefelt.feltDefinisjon.eksternId == FeltType.BEHANDLINGUUID.eksternId
            }?.verdi
                ?: UUID.randomUUID().toString(),
            eksternVersjon = eksternVersjon?.let { it } ?: eksternVersjonTeller++.toString(),
            oppgavetype = oppgavetype,
            status = status,
            endretTidspunkt = LocalDateTime.now(),
            kildeområde = område.eksternId,
            felter = oppgaveFeltverdier.toList(),
            reservasjonsnøkkel = reservasjonsnøkkel,
            aktiv = true
        )
    }

    fun lagre(oppgave: OppgaveV3) {
        return transactionManager.transaction { tx ->
            oppgaverepo.nyOppgaveversjon(oppgave, tx)
            oppgave
        }
    }
}

enum class FeltType(
    val eksternId: String,
    val listetype: Boolean = false,
    val tolkesSom: String = "String"
) {
    BEHANDLINGUUID("behandlingUuid"),
    OPPGAVE_STATUS("oppgavestatus", listetype = true),
    FAGSYSTEM("fagsystem"),
    AKSJONSPUNKT("aksjonspunkt", true),
    RESULTAT_TYPE("resultattype", true),
    TOTRINNSKONTROLL("totrinnskontroll", tolkesSom = "Boolean"),
    BEHANDLINGSSTATUS("behandlingsstatus", true),
    YTELSE_TYPE("ytelsestype", true),
    MOTTATT_DATO("mottattDato"),
    TID_SIDEN_MOTTATT_DATO("tidSidenMottattDato", false, "Duration"),
    REGISTRERT_DATO("registrertDato"),
    AVVENTER_ARBEIDSGIVER("avventerArbeidsgiver", tolkesSom = "Boolean"),
    BESKYTTELSE("beskyttelse", tolkesSom = "String", listetype = true),
    EGEN_ANSATT("egenAnsatt", tolkesSom = "String", listetype = true),
    LØSBART_AKSJONSPUNKT("løsbartAksjonspunkt"),
    LIGGER_HOS_BESLUTTER("liggerHosBeslutter"),
}
