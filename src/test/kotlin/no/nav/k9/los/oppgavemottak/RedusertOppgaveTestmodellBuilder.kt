package no.nav.k9.los.oppgavemottak

import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.*
import no.nav.k9.los.oppgavedefinisjon.omraade.Område
import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRepository
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavefeltDto
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavetypeDto
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavetypeTjeneste
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavetyperDto
import org.koin.test.KoinTest
import org.koin.test.get
import java.time.LocalDateTime

/**
 * Bygger en redusert oppgavemodell for test.
 *
 * Isolasjonsenheten er **området**. Både feltdefinisjoner og oppgavetyper er scopet til område, og
 * [FeltdefinisjonTjeneste.oppdater]/[OppgavetypeTjeneste.oppdater] har erstatt-semantikk — de sletter
 * det som ikke ligger i den innkommende dtoen. Får hver test sitt eget område, er det uproblematisk,
 * og opprydningen i `slettTestområder` fjerner området etterpå.
 *
 * Derfor er default-området avledet av [oppgavetypeId], slik at to testklasser ikke tråkker på
 * hverandre. Tester som eksplisitt trenger et kjent område (f.eks. K9) kan sende inn [område].
 */
class RedusertOppgaveTestmodellBuilder(
    private val oppgavetypeId: String = "redusertTestOppgavetype",
    val område: Område = Område(eksternId = "unittest-$oppgavetypeId"),
): KoinTest {

    private var områdeRepository: OmrådeRepository = get()
    private var feltdefinisjonTjeneste: FeltdefinisjonTjeneste = get()
    private var oppgavetypeTjeneste: OppgavetypeTjeneste = get()


    fun byggOppgavemodell() {
        områdeRepository.lagre(eksternId = område.eksternId)
        feltdefinisjonTjeneste.oppdater(lagFeltdefinisjonDto())
        oppgavetypeTjeneste.oppdater(lagOppgavetypeDto())
    }

    fun lagFeltdefinisjonDto(): FeltdefinisjonerDto {
        return FeltdefinisjonerDto(
            område = område.eksternId,
            feltdefinisjoner = setOf(
                FeltdefinisjonDto(
                    id = "aksjonspunkt",
                    visningsnavn = "Test",
                    beskrivelse = null,
                    listetype = true,
                    tolkesSom = Datatype.STRING,
                    synlighet = Synlighet.UNDER_STREKEN,
                    kodeverkreferanse = null,
                    transientFeltutleder = null,
                ),
                FeltdefinisjonDto(
                    id = "opprettet",
                    visningsnavn = "Test",
                    beskrivelse = null,
                    listetype = false,
                    tolkesSom = Datatype.TIMESTAMP,
                    synlighet = Synlighet.UNDER_STREKEN,
                    kodeverkreferanse = null,
                    transientFeltutleder = null,
                ),
                FeltdefinisjonDto(
                    id = "aktorId",
                    visningsnavn = "Test",
                    beskrivelse = null,
                    listetype = false,
                    tolkesSom = Datatype.STRING,
                    synlighet = Synlighet.UNDER_STREKEN,
                    kodeverkreferanse = null,
                    transientFeltutleder = null,
                ),
                FeltdefinisjonDto(
                    id = "akkumulertVentetidSaksbehandler",
                    visningsnavn = "Test",
                    beskrivelse = null,
                    listetype = false,
                    tolkesSom = Datatype.DURATION,
                    synlighet = Synlighet.INTERNT,
                    kodeverkreferanse = null,
                    transientFeltutleder = null,
                ),
                FeltdefinisjonDto(
                    id = "avventerSaksbehandler",
                    visningsnavn = "Test",
                    beskrivelse = null,
                    listetype = false,
                    tolkesSom = Datatype.BOOLEAN,
                    synlighet = Synlighet.INTERNT,
                    kodeverkreferanse = null,
                    transientFeltutleder = null,
                )
            )
        )
    }

    fun lagOppgavetypeDto(): OppgavetyperDto {
        return OppgavetyperDto(
            område = område.eksternId,
            oppgavetyper = setOf(
                OppgavetypeDto(
                    id = oppgavetypeId,
                    oppgavebehandlingsUrlTemplate = "\${baseUrl}/fagsak/\${K9.saksnummer}/behandling/\${K9.behandlingUuid}?fakta=default&punkt=default",
                    oppgavefelter = setOf(
                        OppgavefeltDto(
                            id = "aksjonspunkt",
                            visPåOppgave = true,
                            påkrevd = true
                        ),
                        OppgavefeltDto(
                            id = "opprettet",
                            visPåOppgave = true,
                            påkrevd = true
                        ),
                        OppgavefeltDto(
                            id = "aktorId",
                            visPåOppgave = true,
                            påkrevd = true
                        ),
                        OppgavefeltDto(
                            id = "akkumulertVentetidSaksbehandler",
                            visPåOppgave = false,
                            påkrevd = false,
                            feltutlederForLagring = "no.nav.k9.los.oppgavemottak.feltutlederforlagring.AkkumulertVentetidSaksbehandler",
                        ),
                        OppgavefeltDto(
                            id = "avventerSaksbehandler",
                            visPåOppgave = false,
                            påkrevd = true
                        )
                    )
                )
            )
        )
    }

    fun lagOppgaveDto(id: String = "test", reservasjonsnøkkel: String = "test", status: String = "AAPEN"): OppgaveDto {
        return OppgaveDto(
            eksternId = oppgavetypeId,
            eksternVersjon = LocalDateTime.now().toString(),
            type = GeneriskOppgaveDtoType(oppgavetypeId, område),
            status = Oppgavestatus.fraKode(status),
            endretTidspunkt = LocalDateTime.now(),
            reservasjonsnøkkel = reservasjonsnøkkel,
            feltverdier = listOf(
                OppgaveFeltverdiDto(
                    nøkkel = "aksjonspunkt",
                    verdi = "9001"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "opprettet",
                    verdi = LocalDateTime.now().toString()
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "aktorId",
                    verdi = "SKAL IKKE LOGGES"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "avventerSaksbehandler",
                    verdi = "true"
                )
            )
        )
    }

    fun lagOppgaveDtoMedManglendeVerdiIObligFelt(): OppgaveDto {
        return OppgaveDto(
            eksternId = oppgavetypeId,
            eksternVersjon = LocalDateTime.now().toString(),
            type = GeneriskOppgaveDtoType(oppgavetypeId, område),
            status = Oppgavestatus.fraKode("AAPEN"),
            endretTidspunkt = LocalDateTime.now(),
            reservasjonsnøkkel = "test",
            feltverdier = listOf(
                OppgaveFeltverdiDto(
                    nøkkel = "aksjonspunkt",
                    verdi = "9001"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "opprettet",
                    verdi = null
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "aktorId",
                    verdi = "SKAL IKKE LOGGES"
                )
            )
        )
    }

}