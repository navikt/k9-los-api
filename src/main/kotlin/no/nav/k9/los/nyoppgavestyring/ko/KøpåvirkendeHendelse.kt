package no.nav.k9.los.nyoppgavestyring.ko

import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.query.db.EksternOppgaveId

interface KøpåvirkendeHendelse
data class Kødefinisjon(val køId : Long) : KøpåvirkendeHendelse
data class KødefinisjonSlettet(val køId : Long) : KøpåvirkendeHendelse

data class OppgaveHendelseMottatt (val fagsystem: Fagsystem, val eksternId : EksternOppgaveId) : KøpåvirkendeHendelse

data class ReservasjonAnnullert (val reservasjonsnøkkel : String) : KøpåvirkendeHendelse
data class ReservasjonEndret (val reservasjonsnøkkel : String) : KøpåvirkendeHendelse
data class ReservasjonTatt (val reservasjonsnøkkel : String) : KøpåvirkendeHendelse

//kunne også hatt egen hendelse for når det plukkes fra front av kø for å prioritere refresh av flere oppgaver i køer som brukes mest aktivt. Plukking fra kø vil uansett trigge ReservasjonTatt
