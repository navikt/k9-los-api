package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import kotliquery.TransactionalSession
import org.slf4j.LoggerFactory

class AktivOgPartisjonertOppgaveAjourholdTjeneste(
    private val partisjonertOppgaveRepository: PartisjonertOppgaveRepository,
) {
    private val log = LoggerFactory.getLogger(AktivOgPartisjonertOppgaveAjourholdTjeneste::class.java)

    fun ajourholdOppgave(innkommendeOppgave: OppgaveV3, internVersjon: Int, tx: TransactionalSession) {
        val ignorerForKøer = gjelderFRISINN(innkommendeOppgave)
        if (ignorerForKøer) {
            log.info("Oppdaterer ikke aktiv oppgave, da hendelsen gjaldt frisinn for oppgaveId ${innkommendeOppgave.eksternId}")
        } else {
            AktivOppgaveRepository.ajourholdAktivOppgave(innkommendeOppgave, internVersjon, tx)
            partisjonertOppgaveRepository.ajourhold(innkommendeOppgave, tx)
        }
    }

    // Det har aldri vært produksjonsstyring i k9-los for FRISINN, så den skal ignoreres for produksjonsstyringsformål inntil alle hendelser på ytelsen er fjernet fra k9-los
    private fun gjelderFRISINN(oppgave: OppgaveV3): Boolean {
        return oppgave.felter.any { it.oppgavefelt.feltDefinisjon.eksternId == "ytelsestype" && it.verdi == "FRISINN" }
    }

}