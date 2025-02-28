package no.nav.k9.los.nyoppgavestyring

import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.klagetillos.K9KlageTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.punsjtillos.K9PunsjTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.tilbaketillos.K9TilbakeTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Datatype
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveFeltverdi
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Repository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.nyoppgavestyring.query.db.OmrådeOgKode
import no.nav.k9.los.nyoppgavestyring.query.db.OppgavefeltMedMer
import no.nav.k9.los.nyoppgavestyring.query.dto.felter.Oppgavefelt
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

    val oppgaveFeltverdier = mutableMapOf<FeltType, OppgaveFeltverdi>()

    val oppgavetype = transactionManager.transaction { tx ->
        val oppgavetype = oppgavetypeRepo.hent(område, definisjonskilde, tx)
        oppgavetype.oppgavetyper
            .firstOrNull { it.eksternId == oppgaveTypeNavn }
            ?: throw IllegalStateException("Fant ikke oppgavetype for $oppgaveTypeNavn i db")
    }

    fun medOppgaveFeltVerdi(feltTypeKode: FeltType, verdi: String): OppgaveTestDataBuilder {
        val oppgavefelt = oppgavetype.oppgavefelter
            .firstOrNull { it.feltDefinisjon.eksternId == feltTypeKode.eksternId }
            ?: throw IllegalStateException("Fant ikke ønsket feltdefinisjon i db")

        oppgaveFeltverdier[feltTypeKode] = OppgaveFeltverdi(null, oppgavefelt, verdi, if (oppgavefelt.feltDefinisjon.tolkesSom == Datatype.INTEGER.kode) verdi.toLong() else null)
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
            eksternId = oppgaveFeltverdier[FeltType.BEHANDLINGUUID]?.verdi ?: UUID.randomUUID().toString(),
            eksternVersjon = eksternVersjon ?: eksternVersjonTeller++.toString(),
            oppgavetype = oppgavetype,
            status = status,
            endretTidspunkt = LocalDateTime.now(),
            kildeområde = område.eksternId,
            felter = oppgaveFeltverdier.values.toList(),
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
    val tolkesSom: String = "String"
) {
    BEHANDLINGUUID("behandlingUuid"),
    BEHANDLING_TYPE("behandlingTypekode"),
    OPPGAVE_STATUS("oppgavestatus"),
    FAGSYSTEM("fagsystem"),
    AKSJONSPUNKT("aksjonspunkt"),
    RESULTAT_TYPE("resultattype"),
    TOTRINNSKONTROLL("totrinnskontroll", tolkesSom = "boolean"),
    BEHANDLINGSSTATUS("behandlingsstatus"),
    YTELSE_TYPE("ytelsestype"),
    MOTTATT_DATO("mottattDato", tolkesSom = "Timestamp"),
    TID_SIDEN_MOTTATT_DATO("tidSidenMottattDato", "Duration"),
    REGISTRERT_DATO("registrertDato", tolkesSom = "Timestamp"),
    AVVENTER_ARBEIDSGIVER("avventerArbeidsgiver", tolkesSom = "boolean"),
    PERSONBESKYTTELSE("personbeskyttelse", tolkesSom = "String"),
    LØSBART_AKSJONSPUNKT("løsbartAksjonspunkt"),
    LIGGER_HOS_BESLUTTER("liggerHosBeslutter", tolkesSom = "boolean"),
    TID_FORSTE_GANG_HOS_BESLUTTER("tidFørsteGangHosBeslutter"),
}

val felter: Map<OmrådeOgKode, OppgavefeltMedMer> = mapOf(
    OmrådeOgKode("K9", FeltType.OPPGAVE_STATUS.eksternId) to OppgavefeltMedMer(
        Oppgavefelt(
        område = "K9",
        kode = FeltType.OPPGAVE_STATUS.eksternId,
        visningsnavn = FeltType.OPPGAVE_STATUS.name,
        tolkes_som = FeltType.OPPGAVE_STATUS.tolkesSom,
        kokriterie = true,
        verdiforklaringerErUttømmende = false,
        verdiforklaringer = emptyList()
    ), null),
    OmrådeOgKode("K9", FeltType.FAGSYSTEM.eksternId) to OppgavefeltMedMer(
        Oppgavefelt(
        område = "K9",
        kode = FeltType.FAGSYSTEM.eksternId,
        visningsnavn = FeltType.FAGSYSTEM.name,
        tolkes_som = FeltType.FAGSYSTEM.tolkesSom,
        kokriterie = true,
        verdiforklaringerErUttømmende = false,
        verdiforklaringer = emptyList()
    ), null),
    OmrådeOgKode("K9", FeltType.MOTTATT_DATO.eksternId) to OppgavefeltMedMer(
        Oppgavefelt(
        område = "K9",
        kode = FeltType.MOTTATT_DATO.eksternId,
        visningsnavn = FeltType.MOTTATT_DATO.name,
        tolkes_som = FeltType.MOTTATT_DATO.tolkesSom,
        kokriterie = true,
        verdiforklaringerErUttømmende = false,
        verdiforklaringer = emptyList()
    ), null),
    OmrådeOgKode("K9", FeltType.LIGGER_HOS_BESLUTTER.eksternId) to OppgavefeltMedMer(
        Oppgavefelt(
        område = "K9",
        kode = FeltType.LIGGER_HOS_BESLUTTER.eksternId,
        visningsnavn = FeltType.LIGGER_HOS_BESLUTTER.name,
        tolkes_som = FeltType.LIGGER_HOS_BESLUTTER.tolkesSom,
        kokriterie = true,
        verdiforklaringerErUttømmende = false,
        verdiforklaringer = emptyList()
    ), null),
)