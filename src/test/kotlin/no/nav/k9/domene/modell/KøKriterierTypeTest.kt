package no.nav.k9.domene.modell

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.k9.tjenester.avdelingsleder.oppgaveko.KriteriumDto
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue


internal class KøKriterierTypeTest {
    private val om = jacksonObjectMapper()

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
    fun `skal deserialisere kodeverk type riktig og validere basert på kodeverk type`() {
        val kodeverkJson = kodeverkJson(KøKriterierType.BEHANDLINGTYPE,
            listOf(BehandlingType.FORSTEGANGSSOKNAD.kode, BehandlingType.REVURDERING.kode))
        val kriteriumDto = om.readValue(kodeverkJson, KriteriumDto::class.java)
        kriteriumDto.valider()
        println(kriteriumDto)
    }

    @Test
    fun `skal kaste feil hvis kodeverk validering feiler`() {
        val kodeverkJson = kodeverkJson(KøKriterierType.BEHANDLINGTYPE,
            listOf(BehandlingType.FORSTEGANGSSOKNAD.kode, FagsakYtelseType.PLEIEPENGER_SYKT_BARN.kode))

        val kriteriumDto = om.readValue(kodeverkJson, KriteriumDto::class.java)

        assertFailsWith(IllegalArgumentException::class) {
            kriteriumDto.valider()
        }
    }

    @Test
    fun `skal serialisere kodeverk type med felttypeKodeverk`() {
        val json = om.writeValueAsString(KøKriterierType.BEHANDLINGTYPE)
        assertTrue(json.contains("felttypeKodeverk"))
        assertTrue(json.contains(BehandlingType::class.java.simpleName))
    }

    @Test
    fun `skal serialisere kodeverk type uten felttypeKodeverk`() {
        val json = om.writeValueAsString(KøKriterierType.FEILUTBETALING)
        assertFalse(json.contains("felttypeKodeverk"))
    }

    private fun feilutbetalingJson(fom: Int?, tom: Int?) = """ 
                {   "id": "${UUID.randomUUID()}",
                    "inkluder": true,
                    "kriterierType": "${KøKriterierType.FEILUTBETALING.kode}",
                    "fom": "$fom",
                    "tom": "$tom"
                }
            """

    private fun kodeverkJson(kodeverkType: KøKriterierType, koder: List<String>) = """ 
                {   "id": "${UUID.randomUUID()}",
                    "inkluder": true,
                    "kriterierType": "${kodeverkType.kode}",
                    "koder": [${koder.joinToString {"\"$it\""}}]
                }
            """
}