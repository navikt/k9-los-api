package no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.cache

import io.opentelemetry.api.trace.Span
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.*
import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.util.InClauseHjelper
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import java.time.Duration
import java.time.LocalDateTime

class PepCacheService(
    private val pepClient: IPepClient,
    private val pepCacheRepository: PepCacheRepository,
    private val transactionalManager: TransactionalManager
) {

    @WithSpan
    fun oppdaterCacheForÅpneOgVentendeOppgaverEldreEnn(gyldighet: Duration = Duration.ofHours(23)) {
        oppdaterCacheForOppgaverMedStatusEldreEnn(gyldighet, setOf(Oppgavestatus.VENTER, Oppgavestatus.AAPEN))
    }

    private fun oppdaterCacheForOppgaverMedStatusEldreEnn(
        gyldighet: Duration = Duration.ofHours(23),
        status: Set<Oppgavestatus>
    ) {
        transactionalManager.transaction { tx ->
            runBlocking(Dispatchers.IO) {
                val oppgaverSomMåOppdateres = hentOppgaverMedStatusOgPepCacheEldreEnn(
                    tidspunkt = LocalDateTime.now() - gyldighet,
                    antall = 1,
                    status = status,
                    tx
                )
                oppgaverSomMåOppdateres.forEach { oppgave -> oppdater(tx, oppgave) }
            }
        }
    }

    private fun hentOppgaverMedStatusOgPepCacheEldreEnn(
        tidspunkt: LocalDateTime = LocalDateTime.now(),
        antall: Int = 1,
        status: Set<Oppgavestatus>,
        tx: TransactionalSession
    ): List<PepCacheInput> {
        val statusParametre = InClauseHjelper.tilParameternavn(status, "status")
        val query = """
                    SELECT o.oppgave_ekstern_id, 
                    (select ov.verdi from oppgavefelt_verdi_part ov where ov.oppgave_id = o.id AND ov.feltdefinisjon_ekstern_id = 'saksnummer' AND ov.oppgavestatus IN ($statusParametre)) as saksnummer,
                    (select ov.verdi from oppgavefelt_verdi_part ov where ov.oppgave_id = o.id AND ov.feltdefinisjon_ekstern_id = 'aktorId' AND ov.oppgavestatus IN ($statusParametre)) as aktor_id,
                    (select ov.verdi from oppgavefelt_verdi_part ov where ov.oppgave_id = o.id AND ov.feltdefinisjon_ekstern_id = 'pleietrengendeAktorId' AND ov.oppgavestatus IN ($statusParametre)) as pleietrengende_aktor_id,
                    (select ov.verdi from oppgavefelt_verdi_part ov where ov.oppgave_id = o.id AND ov.feltdefinisjon_ekstern_id = 'relatertPartAktorid' AND ov.oppgavestatus IN ($statusParametre)) as relatert_part_aktor_id
                    FROM oppgave_v3_part o 
                    LEFT JOIN OPPGAVE_PEP_CACHE opc ON o.oppgave_ekstern_id = opc.ekstern_id
                    WHERE o.oppgavestatus IN ($statusParametre)
                    AND (opc.oppdatert is null OR opc.oppdatert < :grense)
                    ORDER BY opc.oppdatert NULLS FIRST
                    LIMIT :limit
                """.trimIndent()
        return tx.run(
            queryOf(
                query,
                buildMap {
                    put("grense", tidspunkt)
                    put("limit", antall)
                    putAll(InClauseHjelper.parameternavnTilVerdierMap(status.map { it.kode }, "status"))
                }
            ).map { row ->
                PepCacheInput(
                    row.string("oppgave_ekstern_id"),
                    row.stringOrNull("saksnummer"),
                    listOfNotNull(
                        row.stringOrNull("aktor_id"),
                        row.stringOrNull("pleietrengende_aktor_id"),
                        row.stringOrNull("relatert_part_aktor_id")
                    )
                )
            }.asList
        )
    }

    suspend fun oppdater(tx: TransactionalSession, oppgave: PepCacheInput): PepCache {
        return lagPepCacheFra(oppgave).also { nyPepCache -> pepCacheRepository.lagre(nyPepCache, tx) }
    }

    private suspend fun lagPepCacheFra(oppgaveIdOgAktører: PepCacheInput): PepCache {
        val pep = PepCache(
            eksternId = oppgaveIdOgAktører.eksternId,
            kildeområde = "K9",
            kode6 = false,
            kode7 = false,
            egenAnsatt = false,
            oppdatert = LocalDateTime.now()
        )

        return if (oppgaveIdOgAktører.saksnummer != null) {
            pep.oppdater(oppgaveIdOgAktører.saksnummer)
        } else {
            pep.oppdater(oppgaveIdOgAktører.aktører)
        }
    }

    private suspend fun PepCache.oppdater(saksnummer: String): PepCache {
        val diskresjonskoder = pepClient.diskresjonskoderForSak(saksnummer)

        //TODO ikke sette kode7 og egenansatt til samme verdi, det er misvisende ifht modellen som finnes. Det fungerer funksjonelt p.t fordi kode7 og egen ansatt (skjermet) håndteres samlet i køene
        val kode7ellerEgenAnsatt =
            diskresjonskoder.contains(Diskresjonskode.KODE7) || diskresjonskoder.contains(Diskresjonskode.SKJERMET)
        return oppdater(
            kode6 = diskresjonskoder.contains(Diskresjonskode.KODE6),
            kode7 = kode7ellerEgenAnsatt,
            egenAnsatt = kode7ellerEgenAnsatt,
        )
    }

    private suspend fun PepCache.oppdater(aktører: List<String>): PepCache {
        if (aktører.isEmpty()) {
            return oppdater(kode6 = false, kode7 = false, egenAnsatt = false)
        }
        return coroutineScope {
            val requests = aktører.map {
                async(Span.current().asContextElement()) {
                    pepClient.diskresjonskoderForPerson(it)
                }
            }

            val diskresjonskoder = requests
                .awaitAll()
                .flatten()

            //TODO ikke sette kode7 og egenansatt til samme verdi, det er misvisende ifht modellen som finnes. Det fungerer funksjonelt p.t fordi kode7 og egen ansatt (skjermet) håndteres samlet i køene
            val kode7ellerEgenAnsatt =
                diskresjonskoder.contains(Diskresjonskode.KODE7) || diskresjonskoder.contains(Diskresjonskode.SKJERMET)
            oppdater(
                kode6 = diskresjonskoder.contains(Diskresjonskode.KODE6),
                kode7 = kode7ellerEgenAnsatt,
                egenAnsatt = kode7ellerEgenAnsatt,
            )
        }
    }
}

data class PepCacheInput(
    val eksternId: String,
    val saksnummer: String?,
    val aktører: List<String>
)