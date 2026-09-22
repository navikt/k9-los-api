package no.nav.k9.los.oppgavemottak

import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.*
import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRepository
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.*
import org.koin.test.KoinTest
import org.koin.test.get
import java.time.LocalDateTime
import javax.sql.DataSource

/**
 * Bygger en redusert oppgavemodell for test.
 *
 * Isolasjonsenheten er **området**. Både feltdefinisjoner og oppgavetyper er scopet til område, og
 * [FeltdefinisjonTjeneste.oppdater]/[OppgavetypeTjeneste.oppdater] har erstatt-semantikk — de sletter
 * det som ikke ligger i den innkommende dtoen. Ligger det allerede en modell på området (K9 settes
 * opp én gang av `Områdesetup`), feiler den slettingen på fremmednøkkelen fra `oppgavefelt`.
 *
 * [byggOppgavemodell] river derfor ned oppgavemodellen for området før den bygges opp igjen, slik at
 * testen alltid starter fra et kjent utgangspunkt uavhengig av hva som lå der fra før. Tester som
 * kjører mot et område med produksjonsoppsett (K9) må gjenopprette det etterpå — se
 * `OppgaveInnsendingSpec`.
 */
class RedusertOppgaveTestmodellBuilder(
    private val oppgavetypeId: String = "redusertTestOppgavetype",
    val område: Områder = Områder.K9,
): KoinTest {

    private var områdeRepository: OmrådeRepository = get()
    private var feltdefinisjonTjeneste: FeltdefinisjonTjeneste = get()
    private var oppgavetypeTjeneste: OppgavetypeTjeneste = get()
    private var feltdefinisjonRepository: FeltdefinisjonRepository = get()
    private var oppgavetypeRepository: OppgavetypeRepository = get()
    private var dataSource: DataSource = get()


    fun byggOppgavemodell() {
        områdeRepository.lagre(eksternId = område.eksternId)
        slettOppgavemodell()
        feltdefinisjonTjeneste.oppdater(lagFeltdefinisjonDto())
        oppgavetypeTjeneste.oppdater(lagOppgavetypeDto())
    }

    /**
     * Fjerner oppgavetyper og feltdefinisjoner for området, slik at en ny modell kan bygges uten at
     * erstatt-semantikken må slette felter som fortsatt er i bruk. Kalles av [byggOppgavemodell], og
     * av tester som skal gjenopprette produksjonsmodellen for området etterpå.
     *
     * Sletter direkte mot databasen i fremmednøkkelrekkefølge. Oppgavedata (oppgave_v3 m.fl.) peker på
     * oppgavetype, og må være tømt av testopprydningen før dette kalles.
     */
    fun slettOppgavemodell() {
        val områdeId = "(select id from omrade where ekstern_id = '${område.eksternId}')"
        val slettinger = listOf(
            "delete from oppgavefelt where oppgavetype_id in (select id from oppgavetype where omrade_id in $områdeId)",
            "delete from oppgavetype where omrade_id in $områdeId",
            "delete from feltdefinisjon where omrade_id in $områdeId",
        )

        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                slettinger.forEach { statement.execute(it) }
            }
        }

        // Repositoryene cacher modellen per område, og Koin lever hele testkjøringen ut i kotest.
        oppgavetypeRepository.invaliderCache()
        feltdefinisjonRepository.invaliderFeltdefinisjonerCache()
    }

    fun lagFeltdefinisjonDto(): FeltdefinisjonerDto {
        return FeltdefinisjonerDto(
            område = område,
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
            område = område,
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
        val områdeKode = Områder.fraEksternId(område.eksternId)
        return OppgaveDto(
            eksternId = oppgavetypeId,
            eksternVersjon = LocalDateTime.now().toString(),
            type = GeneriskOppgaveDtoType(oppgavetypeId, områdeKode),
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
