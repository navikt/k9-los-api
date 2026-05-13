package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import no.nav.k9.los.nyoppgavestyring.feltutlederforlagring.AkkumulertVentetidSaksbehandler
import no.nav.k9.los.nyoppgavestyring.feltutlederforlagring.GyldigeFeltutledere
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjon
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjoner
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Synlighet
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavefelt
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavetype
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavetyper
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
        oppgavetypeTjeneste.oppdater(område.eksternId, "unittest") { o, _, _ ->
            Oppgavetyper(område = o, oppgavetyper = emptySet())
        }
        feltdefinisjonTjeneste.oppdater(område.eksternId) { o -> lagFeltdefinisjoner(o) }
        oppgavetypeTjeneste.oppdater(område.eksternId, "k9-sak-til-los") { o, fd, gfu ->
            lagOppgavetyper(o, fd, gfu)
        }
    }

    fun lagFeltdefinisjoner(område: Område): Feltdefinisjoner {
        return Feltdefinisjoner(
            område = område,
            feltdefinisjoner = setOf(
                Feltdefinisjon(
                    eksternId = "aksjonspunkt",
                    område = område,
                    visningsnavn = "Test",
                    beskrivelse = null,
                    listetype = true,
                    tolkesSom = "String",
                    synlighet = Synlighet.UNDER_STREKEN,
                    kodeverkreferanse = null,
                    transientFeltutleder = null,
                ),
                Feltdefinisjon(
                    eksternId = "opprettet",
                    område = område,
                    visningsnavn = "Test",
                    beskrivelse = null,
                    listetype = false,
                    tolkesSom = "Date",
                    synlighet = Synlighet.UNDER_STREKEN,
                    kodeverkreferanse = null,
                    transientFeltutleder = null,
                ),
                Feltdefinisjon(
                    eksternId = "aktorId",
                    område = område,
                    visningsnavn = "Test",
                    beskrivelse = null,
                    listetype = false,
                    tolkesSom = "String",
                    synlighet = Synlighet.UNDER_STREKEN,
                    kodeverkreferanse = null,
                    transientFeltutleder = null,
                ),
                Feltdefinisjon(
                    eksternId = "akkumulertVentetidSaksbehandler",
                    område = område,
                    visningsnavn = "Test",
                    beskrivelse = null,
                    listetype = false,
                    tolkesSom = "Duration",
                    synlighet = Synlighet.INTERNT,
                    kodeverkreferanse = null,
                    transientFeltutleder = null,
                ),
                Feltdefinisjon(
                    eksternId = "avventerSaksbehandler",
                    område = område,
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

    fun lagOppgavetyper(område: Område, feltdefinisjoner: Feltdefinisjoner, gyldigeFeltutledere: GyldigeFeltutledere): Oppgavetyper {
        return Oppgavetyper(
            område = område,
            oppgavetyper = setOf(
                Oppgavetype(
                    eksternId = "aksjonspunkt",
                    område = område,
                    definisjonskilde = "k9-sak-til-los",
                    oppgavebehandlingsUrlTemplate = "\${baseUrl}/fagsak/\${K9.saksnummer}/behandling/\${K9.behandlingUuid}?fakta=default&punkt=default",
                    oppgavefelter = setOf(
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon("aksjonspunkt"),
                            visPåOppgave = true,
                            påkrevd = true,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon("opprettet"),
                            visPåOppgave = true,
                            påkrevd = true,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon("aktorId"),
                            visPåOppgave = true,
                            påkrevd = true,
                            defaultverdi = null
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon("akkumulertVentetidSaksbehandler"),
                            visPåOppgave = false,
                            påkrevd = false,
                            defaultverdi = null,
                            feltutleder = gyldigeFeltutledere.hentFeltutleder(AkkumulertVentetidSaksbehandler::class.java.canonicalName)
                        ),
                        Oppgavefelt(
                            feltDefinisjon = feltdefinisjoner.hentFeltdefinisjon("avventerSaksbehandler"),
                            visPåOppgave = false,
                            påkrevd = true,
                            defaultverdi = null
                        )
                    )
                )
            )
        )
    }

    fun lagOppgaveDto(id: String = "test", reservasjonsnøkkel: String = "test", status: String = "AAPEN"): OppgaveDto {
        return OppgaveDto(
            eksternId = "aksjonspunkt",
            eksternVersjon = LocalDateTime.now().toString(),
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
            eksternId = "aksjonspunkt",
            eksternVersjon = LocalDateTime.now().toString(),
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
