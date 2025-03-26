package no.nav.k9.los.tjenester.avdelingsleder

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.extracting
import assertk.assertions.isEmpty
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.kodeverk.KøKriterierType
import no.nav.k9.los.nyoppgavestyring.kodeverk.MerknadType
import no.nav.k9.los.nyoppgavestyring.kodeverk.OppgaveKode
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.tjenester.avdelingsleder.oppgaveko.KriteriumDto
import no.nav.k9.los.tjenester.avdelingsleder.oppgaveko.SaksbehandlerOppgavekoDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.util.*

internal class AvdelingslederTjenesteTest : AbstractK9LosIntegrationTest() {

    private lateinit var avdelingslederTjeneste: AvdelingslederTjeneste
    private lateinit var saksbehandlerRepository: SaksbehandlerRepository

    @BeforeEach
    fun setup() {
        avdelingslederTjeneste = get()
        saksbehandlerRepository = get()
    }

    @Test
    fun `test at vi lagrer flere saksbehandlere på en kø`() {
        runBlocking {
            val saksbehandlere = lagSaksbehandlere()
            saksbehandlere.forEach {
                saksbehandlerRepository.addSaksbehandler(it)
            }

            val id = avdelingslederTjeneste.opprettOppgaveKø()
            val saksbehandlerDto = saksbehandlere.map {
                SaksbehandlerOppgavekoDto(id.id, it.epost, true)
            }.toTypedArray()

            avdelingslederTjeneste.leggFjernSaksbehandlereFraOppgaveKø(saksbehandlerDto)
            val oppgaveKø = avdelingslederTjeneste.hentOppgaveKø(UUID.fromString(id.id))
            assertThat(oppgaveKø.saksbehandlere.size == saksbehandlerDto.size)
        }
    }

    @Test
    fun `test at vi ikke lagrer saksbehandlere på en kø som allerede har disse saksbehandlerene`() {
        runBlocking {
            val saksbehandlere = lagSaksbehandlere()
            saksbehandlere.forEach { saksbehandlerRepository.addSaksbehandler(it) }

            val id = avdelingslederTjeneste.opprettOppgaveKø()
            val saksbehandlerDto = saksbehandlere.map {
                SaksbehandlerOppgavekoDto(id.id, it.epost, true)
            }.toTypedArray()

            avdelingslederTjeneste.leggFjernSaksbehandlereFraOppgaveKø(saksbehandlerDto)
            var oppgaveKø = avdelingslederTjeneste.hentOppgaveKø(UUID.fromString(id.id))
            assertThat(oppgaveKø.saksbehandlere.size == saksbehandlerDto.size)

            avdelingslederTjeneste.leggFjernSaksbehandlereFraOppgaveKø(saksbehandlerDto)
            oppgaveKø = avdelingslederTjeneste.hentOppgaveKø(UUID.fromString(id.id))
            assertThat(oppgaveKø.saksbehandlere.size == saksbehandlerDto.size)
        }
    }

    @Test
    fun `test at vi legger til og fjerner saksbehandlere fra kø`() {
        runBlocking {
            val saksbehandlere = lagSaksbehandlere()
            saksbehandlere.forEach { saksbehandlerRepository.addSaksbehandler(it) }

            val id = avdelingslederTjeneste.opprettOppgaveKø()
            var saksbehandlerDto = saksbehandlere.map {
                SaksbehandlerOppgavekoDto(id.id, it.epost, true)
            }.toTypedArray()

            avdelingslederTjeneste.leggFjernSaksbehandlereFraOppgaveKø(saksbehandlerDto)
            var oppgaveKø = avdelingslederTjeneste.hentOppgaveKø(UUID.fromString(id.id))
            assertThat(oppgaveKø.saksbehandlere.size == saksbehandlerDto.size)

            saksbehandlerDto = saksbehandlere.map {
                SaksbehandlerOppgavekoDto(id.id, it.epost, false)
            }.toTypedArray()

            avdelingslederTjeneste.leggFjernSaksbehandlereFraOppgaveKø(saksbehandlerDto)
            oppgaveKø = avdelingslederTjeneste.hentOppgaveKø(UUID.fromString(id.id))
            assertThat(oppgaveKø.saksbehandlere.isEmpty())
        }
    }

    @Test
    fun `test at vi legger til en og fjerner en saksbehandler fra en kø`() {
        runBlocking {
            val saksbehandlere = lagSaksbehandlere()
            saksbehandlere.forEach { saksbehandlerRepository.addSaksbehandler(it) }

            val id = avdelingslederTjeneste.opprettOppgaveKø()
            var saksbehandlerDto = saksbehandlere.map {
                SaksbehandlerOppgavekoDto(id.id, it.epost, true)
            }.toTypedArray()

            avdelingslederTjeneste.leggFjernSaksbehandlereFraOppgaveKø(saksbehandlerDto)
            var oppgaveKø = avdelingslederTjeneste.hentOppgaveKø(UUID.fromString(id.id))
            assertThat(oppgaveKø.saksbehandlere.size == saksbehandlerDto.size)

            saksbehandlerDto = arrayOf(
                SaksbehandlerOppgavekoDto(id.id, saksbehandlere[0].epost, true),
                SaksbehandlerOppgavekoDto(id.id, saksbehandlere[1].epost, false),
            )

            avdelingslederTjeneste.leggFjernSaksbehandlereFraOppgaveKø(saksbehandlerDto)
            oppgaveKø = avdelingslederTjeneste.hentOppgaveKø(UUID.fromString(id.id))
            assertThat(oppgaveKø.saksbehandlere.size == 1)
            assertThat(oppgaveKø.saksbehandlere.first().epost == saksbehandlere.first().epost)
        }
    }

    private fun lagSaksbehandlere(): List<Saksbehandler> {
        return listOf(
            Saksbehandler(
                id = null,
                brukerIdent = "ident1",
                navn = "navn1",
                epost = "epost1",
                reservasjoner = mutableSetOf(),
                enhet = "enhet"
            ),
            Saksbehandler(
                id = null,
                brukerIdent = "ident2",
                navn = "navn2",
                epost = "epost2",
                reservasjoner = mutableSetOf(),
                enhet = "enhet"
            ),
            Saksbehandler(
                id = null,
                brukerIdent = null,
                navn = null,
                epost = "epost2",
                reservasjoner = mutableSetOf(),
                enhet = null
            )
        )
    }

    @Test
    fun `skal lagre, hente, fjerne og endre flere kriteriumDto`() {
        runBlocking {
            val id = avdelingslederTjeneste.opprettOppgaveKø()
            val køUuid = id.id
            val merknadKriterium = KriteriumDto(
                id = køUuid,
                kriterierType = KøKriterierType.MERKNADTYPE,
                koder = listOf(MerknadType.HASTESAK.kode, MerknadType.VANSKELIG.kode)
            )

            avdelingslederTjeneste.endreKøKriterier(merknadKriterium)

            val køid = UUID.fromString(køUuid)
            var oppgaveKø = avdelingslederTjeneste.hentOppgaveKø(køid)
            assertThat(oppgaveKø.kriterier)
                .extracting { it.kriterierType }
                .containsExactly(KøKriterierType.MERKNADTYPE)

            val feilutbetalingKriterium = KriteriumDto(
                id = køUuid,
                kriterierType = KøKriterierType.FEILUTBETALING,
                fom = 10.toString(),
                tom = 20.toString()
            )

            avdelingslederTjeneste.endreKøKriterier(feilutbetalingKriterium)

            oppgaveKø = avdelingslederTjeneste.hentOppgaveKø(køid)
            assertThat(oppgaveKø.kriterier)
                .extracting { it.kriterierType }
                .containsExactlyInAnyOrder(KøKriterierType.MERKNADTYPE, KøKriterierType.FEILUTBETALING)

            val fjernMerknadKrierium = KriteriumDto(
                id = køUuid,
                kriterierType = KøKriterierType.MERKNADTYPE,
                koder = emptyList()
            )

            avdelingslederTjeneste.endreKøKriterier(fjernMerknadKrierium)
            oppgaveKø = avdelingslederTjeneste.hentOppgaveKø(køid)
            assertThat(oppgaveKø.kriterier)
                .extracting { it.kriterierType }
                .containsExactlyInAnyOrder(KøKriterierType.FEILUTBETALING)

            val fjernFeilUtbetalingKriterium = KriteriumDto(
                id = køUuid,
                kriterierType = KøKriterierType.FEILUTBETALING,
                fom = 10.toString(),
                tom = 20.toString(),
                checked = false
            )

            avdelingslederTjeneste.endreKøKriterier(fjernFeilUtbetalingKriterium)
            oppgaveKø = avdelingslederTjeneste.hentOppgaveKø(køid)
            assertThat(oppgaveKø.kriterier).isEmpty()


            val oppgavekodeKriterium = KriteriumDto(
                id = køUuid,
                kriterierType = KøKriterierType.OPPGAVEKODE,
                koder = listOf(OppgaveKode.SYKDOM.kode, OppgaveKode.ENDELIG_AVKLARING_MANGLER_IM.kode)
            )

            avdelingslederTjeneste.endreKøKriterier(oppgavekodeKriterium)

            oppgaveKø = avdelingslederTjeneste.hentOppgaveKø(køid)
            assertThat(oppgaveKø.kriterier)
                .extracting { it.kriterierType }
                .containsExactlyInAnyOrder(KøKriterierType.OPPGAVEKODE)

            val fjernOppgaveKodeKriterium = KriteriumDto(
                id = køUuid,
                kriterierType = KøKriterierType.OPPGAVEKODE,
                koder = emptyList()
            )

            avdelingslederTjeneste.endreKøKriterier(fjernOppgaveKodeKriterium)
            oppgaveKø = avdelingslederTjeneste.hentOppgaveKø(køid)
            assertThat(oppgaveKø.kriterier).isEmpty()


            val nyeKrav = KriteriumDto(
                id = køUuid,
                kriterierType = KøKriterierType.NYE_KRAV,
                checked = true,
                inkluder = true
            )

            avdelingslederTjeneste.endreKøKriterier(nyeKrav)

            oppgaveKø = avdelingslederTjeneste.hentOppgaveKø(køid)
            assertThat(oppgaveKø.kriterier)
                .extracting { it.kriterierType }
                .containsExactlyInAnyOrder(KøKriterierType.NYE_KRAV)

            val fjernNyeKrav = KriteriumDto(
                id = køUuid,
                kriterierType = KøKriterierType.NYE_KRAV,
                checked = false
            )

            avdelingslederTjeneste.endreKøKriterier(fjernNyeKrav)
            oppgaveKø = avdelingslederTjeneste.hentOppgaveKø(køid)
            assertThat(oppgaveKø.kriterier).isEmpty()
        }

    }

}