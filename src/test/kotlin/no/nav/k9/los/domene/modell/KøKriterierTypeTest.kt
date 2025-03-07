package no.nav.k9.los.domene.modell

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.kodeverk.KøKriterierType
import no.nav.k9.los.nyoppgavestyring.kodeverk.MerknadType
import no.nav.k9.los.tjenester.avdelingsleder.oppgaveko.KriteriumDto
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertFailsWith


internal class KøKriterierTypeTest {
    private val om = jacksonObjectMapper()

    @Test
    fun `skal deserialisere boolean type riktig og validere basert på boolean`() {
        val kriteriumDto = om.readValue(flaggJson(true), KriteriumDto::class.java)
        kriteriumDto.valider()
    }

    @Test
    fun `skal deserialisere beløp type riktig og validere basert på type`() {
        val kriteriumDto = om.readValue(feilutbetalingJson(10, 100), KriteriumDto::class.java)
        kriteriumDto.valider()
    }

    @Test
    fun `skal kaste feil hvis beløp validering feiler`() {
        val kriteriumDto = om.readValue(feilutbetalingJson(null, null), KriteriumDto::class.java)
        assertFailsWith(IllegalArgumentException::class) {
            kriteriumDto.valider()
        }
    }

    @Test
    fun `skal kaste feil hvis boolean validering feiler`() {
        val kriteriumDto = om.readValue(flaggJson(null), KriteriumDto::class.java)
        assertFailsWith(IllegalArgumentException::class) {
            kriteriumDto.valider()
        }
    }

    @Test
    fun `skal deserialisere kodeverk type riktig og validere basert på kodeverk type`() {
        val kodeverkJson = kodeverkJson(
            KøKriterierType.MERKNADTYPE,
            listOf(MerknadType.HASTESAK.kode, MerknadType.VANSKELIG.kode))
        val kriteriumDto = om.readValue(kodeverkJson, KriteriumDto::class.java)
        kriteriumDto.valider()
    }

    @Test
    fun `skal kaste feil hvis kodeverk validering feiler`() {
        val kodeverkJson = kodeverkJson(
            KøKriterierType.MERKNADTYPE,
            listOf(MerknadType.HASTESAK.kode, FagsakYtelseType.PPN.kode))

        val kriteriumDto = om.readValue(kodeverkJson, KriteriumDto::class.java)

        assertFailsWith(IllegalArgumentException::class) {
            kriteriumDto.valider()
        }
    }

    @Test
    fun `skal serialisere kodeverk type med felttypeKodeverk`() {
        val json = om.writeValueAsString(KøKriterierType.MERKNADTYPE)
        assertThat(json.contains("felttypeKodeverk")).isTrue()
    }

    @Test
    fun `skal serialisere kodeverk type uten felttypeKodeverk`() {
        val json = om.writeValueAsString(KøKriterierType.FEILUTBETALING)
        assertThat(json.contains("felttypeKodeverk")).isFalse()
    }


    private fun flaggJson(flagg: Boolean?) = """ 
                {   "id": "${UUID.randomUUID()}",
                    "kriterierType": "${KøKriterierType.NYE_KRAV.kode}",                   
                    "checked" : "true",
                    "inkluder" : "$flagg"
                
                }
            """

    private fun feilutbetalingJson(fom: Int?, tom: Int?) = """ 
                {   "id": "${UUID.randomUUID()}",
                    "kriterierType": "${KøKriterierType.FEILUTBETALING.kode}",
                    "fom": "$fom",
                    "tom": "$tom"
                }
            """

    private fun kodeverkJson(kodeverkType: KøKriterierType, koder: List<String>) = """ 
                {   "id": "${UUID.randomUUID()}",
                    "kriterierType": "${kodeverkType.kode}",
                    "koder": [${koder.joinToString {"\"$it\""}}]
                }
            """
}