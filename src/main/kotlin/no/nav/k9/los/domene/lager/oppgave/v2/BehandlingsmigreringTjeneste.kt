package no.nav.k9.los.domene.lager.oppgave.v2

import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventRepository
import org.slf4j.LoggerFactory
import java.util.*

open class BehandlingsmigreringTjeneste(
    private val behandlingProsessEventK9Repository: K9SakEventRepository,
) {
    private val log = LoggerFactory.getLogger(BehandlingsmigreringTjeneste::class.java)


    fun hentBehandlingFraTidligereProsessEvents(eksternId: String): Behandling? {
        return try {
            val k9sakModell = behandlingProsessEventK9Repository.hent(UUID.fromString(eksternId))
            val sisteEvent = k9sakModell.sisteEvent()

            if (k9sakModell.oppgave().fagsakYtelseType == FagsakYtelseType.FRISINN) {
                return null // Skal ikke opprette oppgaver for frisinn
            }

            Behandling.ny(
                eksternId,
                fagsystem = Fagsystem.K9SAK,
                ytelseType = FagsakYtelseType.fraKode(sisteEvent.ytelseTypeKode),
                behandlingType = sisteEvent.behandlingTypeKode,
                søkersId = Ident(sisteEvent.aktørId, Ident.IdType.AKTØRID),
                opprettet = k9sakModell.førsteEvent().opprettetBehandling
            )
        } catch (e: Exception) {
            log.warn("Fant ikke k9sakModell for eksternId $eksternId")
            null
        }
    }
}