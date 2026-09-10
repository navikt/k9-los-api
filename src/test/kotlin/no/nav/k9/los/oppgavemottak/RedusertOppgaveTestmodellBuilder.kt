package no.nav.k9.los.oppgavemottak

import no.nav.k9.los.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.FeltdefinisjonDto
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.FeltdefinisjonerDto
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.Synlighet
import no.nav.k9.los.oppgavedefinisjon.omraade.Område
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavefeltDto
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavetypeDto
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavetyperDto
import org.koin.test.KoinTest
import org.koin.test.get
import java.time.LocalDateTime

class RedusertOppgaveTestmodellBuilder(
    val område: Område = Område(eksternId = Områder.K9.eksternId)
) : KoinTest {

    /**
     * Seeder K9-området med produksjons-feltdefinisjoner og -oppgavetyper via OmrådeSetup.
     * Nødvendig for JUnit-tester som truncater alle tabeller etter hver test.
     * Idempotent, og allerede kjørt én gang for Kotest-tester i ProjectConfig.beforeProject.
     */
    fun byggOppgavemodell() {
        get<OmrådeSetup>().setup()
    }

    fun lagFeltdefinisjonDto(): FeltdefinisjonerDto {
        return FeltdefinisjonerDto(
            område = område.tilOmrådeEnum(),
            feltdefinisjoner = setOf(
                FeltdefinisjonDto(
                    id = "aksjonspunkt",
                    visningsnavn = "Test",
                    beskrivelse = null,
                    listetype = true,
                    tolkesSom = "String",

                    synlighet = Synlighet.UNDER_STREKEN,
                    kodeverkreferanse = null,
                    transientFeltutleder = null,
                ),
                FeltdefinisjonDto(
                    id = "opprettet",
                    visningsnavn = "Test",
                    beskrivelse = null,
                    listetype = false,
                    tolkesSom = "Timestamp",

                    synlighet = Synlighet.UNDER_STREKEN,
                    kodeverkreferanse = null,
                    transientFeltutleder = null,
                ),
                FeltdefinisjonDto(
                    id = "aktorId",
                    visningsnavn = "Test",
                    beskrivelse = null,
                    listetype = false,
                    tolkesSom = "String",

                    synlighet = Synlighet.UNDER_STREKEN,
                    kodeverkreferanse = null,
                    transientFeltutleder = null,
                ),
                FeltdefinisjonDto(
                    id = "akkumulertVentetidSaksbehandler",
                    visningsnavn = "Test",
                    beskrivelse = null,
                    listetype = false,
                    tolkesSom = "Duration",
                    synlighet = Synlighet.INTERNT,
                    kodeverkreferanse = null,
                    transientFeltutleder = null,
                ),
                FeltdefinisjonDto(
                    id = "avventerSaksbehandler",
                    visningsnavn = "Test",
                    beskrivelse = null,
                    listetype = false,
                    tolkesSom = "boolean",
                    synlighet = Synlighet.INTERNT,
                    kodeverkreferanse = null,
                    transientFeltutleder = null,
                )
            )
        )
    }

    fun lagOppgavetypeDto(): OppgavetyperDto {
        return OppgavetyperDto(
            område = område.tilOmrådeEnum(),
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
                            påkrevd = false,
                            feltutleder = "no.nav.k9.los.oppgavemottak.feltutlederforlagring.AkkumulertVentetidSaksbehandler",
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
            eksternId = id,
            eksternVersjon = LocalDateTime.now().toString(),
            område = område.tilOmrådeEnum(),
            kildeområde = område.tilOmrådeEnum(),
            type = "k9sak",
            status = status,
            endretTidspunkt = LocalDateTime.now(),
            reservasjonsnøkkel = reservasjonsnøkkel,
            feltverdier = listOf(
                OppgaveFeltverdiDto(
                    nøkkel = "aksjonspunkt",
                    verdi = "9001"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "aktorId",
                    verdi = "SKAL IKKE LOGGES"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "avventerSaksbehandler",
                    verdi = "true"
                ),
                // Påkrevde felter i k9sak-oppgavetypen (seedet fra k9-oppgavetyper-k9sak.json)
                OppgaveFeltverdiDto(nøkkel = "behandlingUuid", verdi = "00000000-0000-0000-0000-000000000000"),
                OppgaveFeltverdiDto(nøkkel = "fagsystem", verdi = "K9SAK"),
                OppgaveFeltverdiDto(nøkkel = "saksnummer", verdi = "TESTSAK1"),
                OppgaveFeltverdiDto(nøkkel = "resultattype", verdi = "IKKE_FASTSATT"),
                OppgaveFeltverdiDto(nøkkel = "ytelsestype", verdi = "PSB"),
                OppgaveFeltverdiDto(nøkkel = "behandlingsstatus", verdi = "UTRED"),
                OppgaveFeltverdiDto(nøkkel = "behandlingTypekode", verdi = "BT-002"),
                OppgaveFeltverdiDto(nøkkel = "totrinnskontroll", verdi = "false"),
                OppgaveFeltverdiDto(nøkkel = "avventerSøker", verdi = "false"),
                OppgaveFeltverdiDto(nøkkel = "avventerArbeidsgiver", verdi = "false"),
                OppgaveFeltverdiDto(nøkkel = "avventerTekniskFeil", verdi = "false"),
                OppgaveFeltverdiDto(nøkkel = "avventerAnnet", verdi = "false"),
                OppgaveFeltverdiDto(nøkkel = "avventerAnnetIkkeSaksbehandlingstid", verdi = "false"),
                OppgaveFeltverdiDto(nøkkel = "helautomatiskBehandlet", verdi = "false"),
                OppgaveFeltverdiDto(nøkkel = "utenlandstilsnitt", verdi = "false"),
                OppgaveFeltverdiDto(nøkkel = "direkteutbetaling", verdi = "false")
            )
        )
    }

    fun lagOppgaveDtoMedManglendeVerdiIObligFelt(): OppgaveDto {
        return lagOppgaveDto().copy(
            feltverdier = listOf(
                OppgaveFeltverdiDto(
                    nøkkel = "aksjonspunkt",
                    verdi = "9001"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "aktorId",
                    verdi = null
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "avventerSaksbehandler",
                    verdi = "true"
                )
            )
        )
    }
}
