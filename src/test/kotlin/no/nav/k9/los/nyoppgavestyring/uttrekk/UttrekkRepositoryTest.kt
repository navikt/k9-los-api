package no.nav.k9.los.nyoppgavestyring.uttrekk

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.lagretsok.LagretSøk
import no.nav.k9.los.nyoppgavestyring.lagretsok.LagretSøkRepository
import no.nav.k9.los.nyoppgavestyring.lagretsok.OpprettLagretSøk
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.test.get

class UttrekkRepositoryTest : AbstractK9LosIntegrationTest() {

    private lateinit var uttrekkRepository: UttrekkRepository
    private lateinit var lagretSøkRepository: LagretSøkRepository
    private lateinit var saksbehandlerRepository: SaksbehandlerRepository
    private var saksbehandlerId: Long = 0L
    private lateinit var testQuery: OppgaveQuery

    @BeforeEach
    fun setup() {
        uttrekkRepository = get()
        lagretSøkRepository = get()
        saksbehandlerRepository = get()

        runBlocking {
            saksbehandlerRepository.addSaksbehandler(
                Saksbehandler(
                    id = null,
                    brukerIdent = "test",
                    navn = "Test Testersen",
                    epost = "test@nav.no",
                    reservasjoner = mutableSetOf(),
                    enhet = null,
                )
            )
            val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedEpost("test@nav.no")!!
            saksbehandlerId = saksbehandler.id!!
            val lagretSøk = LagretSøk.opprettSøk(
                OpprettLagretSøk(tittel = "Test søk"),
                saksbehandler,
                false
            )
            lagretSøkRepository.opprett(lagretSøk)
            testQuery = lagretSøk.query
        }
    }

    @Test
    fun `skal opprette og hente uttrekk`() {
        val uttrekk = Uttrekk.opprettUttrekk(
            query = testQuery,
            typeKjoring = TypeKjøring.OPPGAVER,
            lagetAv = saksbehandlerId,
            timeout = 30
        )

        val id = uttrekkRepository.opprett(uttrekk)

        val hentetUttrekk = uttrekkRepository.hent(id)
        assertThat(hentetUttrekk).isNotNull()
        assertThat(hentetUttrekk!!.id).isEqualTo(id)
        assertThat(hentetUttrekk.lagetAv).isEqualTo(saksbehandlerId)
        assertThat(hentetUttrekk.timeout).isEqualTo(30)
        assertThat(hentetUttrekk.status).isEqualTo(UttrekkStatus.OPPRETTET)
        assertThat(uttrekkRepository.hentResultat(id)).isNull()
        assertThat(hentetUttrekk.antall).isNull()
    }

    @Test
    fun `skal returnere null når uttrekk ikke finnes`() {
        val hentetUttrekk = uttrekkRepository.hent(999L)
        assertThat(hentetUttrekk).isNull()
    }

    @Test
    fun `skal oppdatere eksisterende uttrekk`() {
        val uttrekk = Uttrekk.opprettUttrekk(
            query = testQuery,
            typeKjoring = TypeKjøring.OPPGAVER,
            lagetAv = saksbehandlerId,
            timeout = 30
        )

        val id = uttrekkRepository.opprett(uttrekk)
        val hentetUttrekk = uttrekkRepository.hent(id)!!

        hentetUttrekk.markerSomKjører()
        uttrekkRepository.oppdater(hentetUttrekk)

        val oppdatertUttrekk = uttrekkRepository.hent(id)!!
        assertThat(oppdatertUttrekk.status).isEqualTo(UttrekkStatus.KJØRER)

        hentetUttrekk.markerSomFullført(0)
        uttrekkRepository.oppdater(hentetUttrekk, "[]")

        val fullførtUttrekk = uttrekkRepository.hent(id)!!
        assertThat(fullførtUttrekk.status).isEqualTo(UttrekkStatus.FULLFØRT)
        assertThat(uttrekkRepository.hentResultat(id)).isEqualTo("[]")
        assertThat(fullførtUttrekk.fullførtTidspunkt).isNotNull()
        assertThat(fullførtUttrekk.antall).isEqualTo(0)
    }

    @Test
    fun `skal kaste exception ved oppdatering av ikke-eksisterende uttrekk`() {
        val uttrekk = Uttrekk.fraEksisterende(
            id = 999L,
            opprettetTidspunkt = java.time.LocalDateTime.now(),
            status = UttrekkStatus.KJØRER,
            query = testQuery,
            typeKjoring = TypeKjøring.OPPGAVER,
            lagetAv = saksbehandlerId,
            timeout = 30,
            feilmelding = null,
            startetTidspunkt = java.time.LocalDateTime.now(),
            fullførtTidspunkt = null,
            antall = null
        )

        val exception = assertThrows<IllegalStateException> {
            uttrekkRepository.oppdater(uttrekk)
        }

        assertThat(exception.message).isEqualTo("Feilet ved update på uttrekk. Uttrekk med id 999 finnes ikke.")
    }

    @Test
    fun `skal slette uttrekk`() {
        val uttrekk = Uttrekk.opprettUttrekk(
            query = testQuery,
            typeKjoring = TypeKjøring.OPPGAVER,
            lagetAv = saksbehandlerId,
            timeout = 30
        )

        val id = uttrekkRepository.opprett(uttrekk)
        assertThat(uttrekkRepository.hent(id)).isNotNull()

        uttrekkRepository.slett(id)

        val uttrekkEtterSletting = uttrekkRepository.hent(id)
        assertThat(uttrekkEtterSletting).isNull()
    }

    @Test
    fun `skal hente alle uttrekk`() {
        val uttrekk1 = Uttrekk.opprettUttrekk(
            query = testQuery,
            typeKjoring = TypeKjøring.OPPGAVER,
            lagetAv = saksbehandlerId,
            timeout = 30
        )
        val uttrekk2 = Uttrekk.opprettUttrekk(
            query = testQuery,
            typeKjoring = TypeKjøring.OPPGAVER,
            lagetAv = saksbehandlerId,
            timeout = 30
        )

        uttrekkRepository.opprett(uttrekk1)
        uttrekkRepository.opprett(uttrekk2)

        val alleUttrekk = uttrekkRepository.hentAlle()
        assertThat(alleUttrekk.size >= 2).isEqualTo(true)
    }

    @Test
    fun `skal hente uttrekk for saksbehandler`() {
        // Opprett en annen saksbehandler for å teste filtreringen
        val annenSaksbehandlerId = runBlocking {
            saksbehandlerRepository.addSaksbehandler(
                Saksbehandler(
                    id = null,
                    brukerIdent = "test2",
                    navn = "Test Testersen 2",
                    epost = "test2@nav.no",
                    reservasjoner = mutableSetOf(),
                    enhet = null,
                )
            )
            saksbehandlerRepository.finnSaksbehandlerMedEpost("test2@nav.no")!!.id!!
        }

        val uttrekk1 = Uttrekk.opprettUttrekk(
            query = testQuery,
            typeKjoring = TypeKjøring.OPPGAVER,
            lagetAv = saksbehandlerId,
            timeout = 30
        )
        val uttrekk2 = Uttrekk.opprettUttrekk(
            query = testQuery,
            typeKjoring = TypeKjøring.OPPGAVER,
            lagetAv = saksbehandlerId,
            timeout = 30
        )
        val uttrekk3 = Uttrekk.opprettUttrekk(
            query = testQuery,
            typeKjoring = TypeKjøring.OPPGAVER,
            lagetAv = annenSaksbehandlerId,
            timeout = 30
        )

        uttrekkRepository.opprett(uttrekk1)
        uttrekkRepository.opprett(uttrekk2)
        uttrekkRepository.opprett(uttrekk3)

        val uttrekkForSaksbehandler = uttrekkRepository.hentForSaksbehandler(saksbehandlerId)
        assertThat(uttrekkForSaksbehandler).hasSize(2)
        assertThat(uttrekkForSaksbehandler.all { it.lagetAv == saksbehandlerId }).isEqualTo(true)
    }

    @Test
    fun `skal opprette uttrekk med TypeKjøring ANTALL`() {
        val uttrekk = Uttrekk.opprettUttrekk(
            query = testQuery,
            typeKjoring = TypeKjøring.ANTALL,
            lagetAv = saksbehandlerId,
            timeout = 30
        )

        val id = uttrekkRepository.opprett(uttrekk)
        val hentetUttrekk = uttrekkRepository.hent(id)

        assertThat(hentetUttrekk).isNotNull()
        assertThat(hentetUttrekk!!.typeKjøring).isEqualTo(TypeKjøring.ANTALL)
    }

    @Test
    fun `skal opprette uttrekk med TypeKjøring OPPGAVER`() {
        val uttrekk = Uttrekk.opprettUttrekk(
            query = testQuery,
            typeKjoring = TypeKjøring.OPPGAVER,
            lagetAv = saksbehandlerId,
            timeout = 30
        )

        val id = uttrekkRepository.opprett(uttrekk)
        val hentetUttrekk = uttrekkRepository.hent(id)

        assertThat(hentetUttrekk).isNotNull()
        assertThat(hentetUttrekk!!.typeKjøring).isEqualTo(TypeKjøring.OPPGAVER)
    }

    @Test
    fun `skal sette feilmelding når uttrekk feiler`() {
        val uttrekk = Uttrekk.opprettUttrekk(
            query = testQuery,
            typeKjoring = TypeKjøring.OPPGAVER,
            lagetAv = saksbehandlerId,
            timeout = 30
        )

        val id = uttrekkRepository.opprett(uttrekk)
        val hentetUttrekk = uttrekkRepository.hent(id)!!

        hentetUttrekk.markerSomKjører()
        uttrekkRepository.oppdater(hentetUttrekk)

        hentetUttrekk.markerSomFeilet("Database connection timeout")
        uttrekkRepository.oppdater(hentetUttrekk)

        val feiletUttrekk = uttrekkRepository.hent(id)!!
        assertThat(feiletUttrekk.status).isEqualTo(UttrekkStatus.FEILET)
        assertThat(feiletUttrekk.feilmelding).isEqualTo("Database connection timeout")
        assertThat(uttrekkRepository.hentResultat(id)).isNull()
        assertThat(feiletUttrekk.fullførtTidspunkt).isNotNull()
    }
}
