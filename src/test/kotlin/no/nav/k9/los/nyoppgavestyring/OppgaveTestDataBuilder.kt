package no.nav.k9.los.nyoppgavestyring

import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
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
    val område: Område = Område(1, "K9"),
    val definisjonskilde: String = "k9-sak-til-los",
    val oppgaveTypeNavn: String = "k9sak"
): KoinTest {
    val transactionManager = get<TransactionalManager>()
    val oppgavetypeRepo = get<OppgavetypeRepository>()
    val oppgaverepo = get<OppgaveV3Repository>()
    val k9SakTilLosAdapterTjeneste = get<K9SakTilLosAdapterTjeneste>()

    init {
        k9SakTilLosAdapterTjeneste.setup()
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


    fun lagOgLagre(): OppgaveV3 {
        val antall = oppgaverepo.tellAntall().first
        return transactionManager.transaction { tx ->
            val oppgave = OppgaveV3(
                id = antall,
                eksternId = oppgaveFeltverdier.firstOrNull {
                    it.oppgavefelt.feltDefinisjon.eksternId == FeltType.BEHANDLINGUUID.eksternId }?.verdi
                    ?: UUID.randomUUID().toString(),
                eksternVersjon = "0",
                oppgavetype = oppgavetype,
                status = Oppgavestatus.AAPEN,
                endretTidspunkt = LocalDateTime.now(),
                kildeområde = område.eksternId,
                felter = oppgaveFeltverdier.toList(),
                reservasjonsnøkkel = ""
            )
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
