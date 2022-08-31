package no.nav.k9.domene.lager.oppgave.v3.oppgave

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.k9.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.integrasjon.rest.RequestContextService
import org.koin.ktor.ext.inject
import java.lang.IllegalArgumentException

internal fun Route.OppgaveV3Api() {
    val oppgaveV3Repository by inject<OppgaveV3Repository>()
    val requestContextService by inject<RequestContextService>()
    val transactionalManager by inject<TransactionalManager>()

    put {
        requestContextService.withRequestContext(call) {
            val oppgaveDto = call.receive<OppgaveDto>()
            transactionalManager.transaction { tx ->
                val oppgavetype = oppgaveV3Repository.hentOppgaveType(oppgaveDto.kildeområde, oppgaveDto.type, tx)
                    ?: throw IllegalArgumentException("Kan ikke legge til oppgave på en oppgavetype som ikke er definert")

                oppgavetype.valider(oppgaveDto)
                val innkommendeOppgave = OppgaveV3(oppgaveDto, oppgavetype)

                oppgaveV3Repository.lagre(innkommendeOppgave, tx)
            }

            call.respond("OK")
        }
    }

    //put(oppgave) -- opprett/oppdater
    //get

}