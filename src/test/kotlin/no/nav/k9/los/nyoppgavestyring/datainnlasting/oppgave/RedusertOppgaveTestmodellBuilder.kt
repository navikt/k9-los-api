package no.nav.k9.los.nyoppgavestyring.datainnlasting.oppgave

import no.nav.k9.los.nyoppgavestyring.datainnlasting.feltdefinisjon.FeltdefinisjonDto
import no.nav.k9.los.nyoppgavestyring.datainnlasting.feltdefinisjon.FeltdefinisjonTjeneste
import no.nav.k9.los.nyoppgavestyring.datainnlasting.feltdefinisjon.FeltdefinisjonerDto
import no.nav.k9.los.nyoppgavestyring.datainnlasting.omraade.Område
import no.nav.k9.los.nyoppgavestyring.datainnlasting.omraade.OmrådeRepository
import no.nav.k9.los.nyoppgavestyring.datainnlasting.oppgavetype.OppgavefeltDto
import no.nav.k9.los.nyoppgavestyring.datainnlasting.oppgavetype.OppgavetypeDto
import no.nav.k9.los.nyoppgavestyring.datainnlasting.oppgavetype.OppgavetypeTjeneste
import no.nav.k9.los.nyoppgavestyring.datainnlasting.oppgavetype.OppgavetyperDto
import org.koin.test.KoinTest
import org.koin.test.get
import java.time.LocalDateTime

class RedusertOppgaveTestmodellBuilder(
    val område: Område = Område(eksternId = "OppgaveV3Test")
): KoinTest {

    private var områdeRepository: OmrådeRepository = get()
    private var feltdefinisjonTjeneste: FeltdefinisjonTjeneste = get()
    private var oppgavetypeTjeneste: OppgavetypeTjeneste = get()


    fun byggOppgavemodell() {
        områdeRepository.lagre(eksternId = område.eksternId)
        oppgavetypeTjeneste.oppdater(
            OppgavetyperDto(
                område.eksternId,
                definisjonskilde = "unittest",
                oppgavetyper = emptySet()
            )
        )
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
                    listetype = true,
                    tolkesSom = "String",
                    true,
                    false,
                    kodeverkreferanse = null,
                    transientFeltutleder = null,
                ),
                FeltdefinisjonDto(
                    id = "opprettet",
                    visningsnavn = "Test",
                    listetype = false,
                    tolkesSom = "Date",
                    true,
                    false,
                    kodeverkreferanse = null,
                    transientFeltutleder = null,
                ),
                FeltdefinisjonDto(
                    id = "aktorId",
                    visningsnavn = "Test",
                    listetype = false,
                    tolkesSom = "String",
                    true,
                    false,
                    kodeverkreferanse = null,
                    transientFeltutleder = null,

                    ),
                FeltdefinisjonDto(
                    id = "akkumulertVentetidSaksbehandler",
                    visningsnavn = "Test",
                    listetype = false,
                    tolkesSom = "Duration",
                    false,
                    false,
                    kodeverkreferanse = null,
                    transientFeltutleder = null,
                ),
                FeltdefinisjonDto(
                    id = "avventerSaksbehandler",
                    visningsnavn = "Test",
                    listetype = false,
                    tolkesSom = "boolean",
                    false,
                    false,
                    kodeverkreferanse = null,
                    transientFeltutleder = null,
                )
            )
        )
    }

    fun lagOppgavetypeDto(): OppgavetyperDto {
        return OppgavetyperDto(
            område = område.eksternId,
            definisjonskilde = "k9-sak-til-los",
            oppgavetyper = setOf(
                OppgavetypeDto(
                    id = "aksjonspunkt",
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
                            påkrevd = true,
                            feltutleder = "no.nav.k9.los.nyoppgavestyring.feltutlederforlagring.AkkumulertVentetidSaksbehandler",
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
            id = "aksjonspunkt",
            versjon = LocalDateTime.now().toString(),
            område = område.eksternId,
            kildeområde = "k9-sak-til-los",
            type = "aksjonspunkt",
            status = status,
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
                )
            )
        )
    }

    fun lagOppgaveDtoMedManglendeVerdiIObligFelt(): OppgaveDto {
        return OppgaveDto(
            id = "aksjonspunkt",
            versjon = LocalDateTime.now().toString(),
            område = område.eksternId,
            kildeområde = "k9-sak-til-los",
            type = "aksjonspunkt",
            status = "ÅPEN",
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