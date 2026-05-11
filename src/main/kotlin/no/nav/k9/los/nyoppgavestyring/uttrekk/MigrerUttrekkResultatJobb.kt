package no.nav.k9.los.nyoppgavestyring.uttrekk

import com.fasterxml.jackson.databind.JsonNode
import kotliquery.queryOf
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import org.slf4j.LoggerFactory

class MigrerUttrekkResultatJobb(
    private val uttrekkRepository: UttrekkRepository,
) {
    private val log = LoggerFactory.getLogger(MigrerUttrekkResultatJobb::class.java)

    fun kjør() {
        val ider = uttrekkRepository.hentAlleIderMedResultat()
        if (ider.isEmpty()) {
            log.info("Ingen uttrekk med resultat å migrere")
            return
        }

        log.info("Sjekker ${ider.size} uttrekk for gammelt resultatformat")
        var antallMigrert = 0

        for (id in ider) {
            val resultatJson = uttrekkRepository.hentResultat(id) ?: continue
            if (erGammeltFormat(resultatJson)) {
                val konvertert = konverterTilNyttFormat(resultatJson)
                uttrekkRepository.oppdaterResultat(id, konvertert)
                antallMigrert++
            }
        }

        log.info("Migrering ferdig: $antallMigrert av ${ider.size} uttrekk ble konvertert til nytt format")
    }

    companion object {
        private val objectMapper = LosObjectMapper.instance

        fun erGammeltFormat(resultatJson: String): Boolean {
            val node = objectMapper.readTree(resultatJson)
            if (!node.isArray || node.size() == 0) return false
            val førsteElement = node[0]
            return førsteElement.has("felter") && førsteElement.has("id") && førsteElement["id"].isObject
        }

        fun konverterTilNyttFormat(resultatJson: String): String {
            val node = objectMapper.readTree(resultatJson)
            val rader = node.map { element ->
                val eksternId = element["id"]["eksternId"].asText()
                val kolonner = element["felter"].map { felt -> jsonNodeTilVerdi(felt["verdi"]) }
                UttrekkRad(id = eksternId, kolonner = kolonner)
            }
            return objectMapper.writeValueAsString(rader)
        }

        private fun jsonNodeTilVerdi(node: JsonNode?): Any? {
            return when {
                node == null || node.isNull -> null
                node.isTextual -> node.asText()
                node.isIntegralNumber -> node.longValue()
                node.isFloatingPointNumber -> node.doubleValue()
                node.isBoolean -> node.booleanValue()
                else -> node.asText()
            }
        }

        // Extensionfunction siden det er repositorymetode kun brukt her, som skal slettes etter kjøring
        private fun UttrekkRepository.hentAlleIderMedResultat(): List<Long> {
            return transactionalManager.transaction {
                it.run(
                    queryOf(
                        """
                SELECT id FROM uttrekk WHERE resultat IS NOT NULL
            """.trimIndent()
                    ).map { row -> row.long("id") }.asList
                )
            }
        }

        // Extensionfunction siden det er repositorymetode kun brukt her, som skal slettes etter kjøring
        fun UttrekkRepository.oppdaterResultat(id: Long, resultatJson: String) {
            transactionalManager.transaction {
                it.run(
                    queryOf(
                        """
                UPDATE uttrekk SET resultat = :resultat::jsonb WHERE id = :id
            """.trimIndent(),
                        mapOf("id" to id, "resultat" to resultatJson)
                    ).asUpdate
                )
            }
        }
    }
}
