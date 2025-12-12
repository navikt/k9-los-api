package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import org.koin.test.KoinTest
import org.koin.test.get

class OppgaveInnsendingSpec: KoinTest, FreeSpec(){
    val oppgavemodellBuilder = RedusertOppgaveTestmodellBuilder()
    val oppgaveV3Tjeneste = get<OppgaveV3Tjeneste>()
    val transactionalManager = get<TransactionalManager>()

    init {
        "En oppgaveDto pakket inn i NyOppgaveversjon" - {
            oppgavemodellBuilder.byggOppgavemodell()
            val oppgaveDto = oppgavemodellBuilder.lagOppgaveDto()
            val innsending = NyOppgaveversjon(oppgaveDto)
            "og eksternVersjon ikke har blitt sendt inn før" - {
                "skal gi ny oppgaveversjon" {
                    val retur = transactionalManager.transaction { tx ->
                        oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(innsending, tx)
                    }

                    retur.shouldBeInstanceOf<OppgaveV3>()
                }
            }
            "og eksternVersjon har blitt sendt inn før" - {
                transactionalManager.transaction { tx ->
                    oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(innsending, tx)
                }
                "skal avvise innsendingen og returnere NULL" {
                    val retur = transactionalManager.transaction { tx ->
                        oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(innsending, tx)
                    }

                    retur shouldBe null
                }
            }
        }
        "En oppgaveDto pakket inn i VaskOppgaveversjon" - {
            oppgavemodellBuilder.byggOppgavemodell()
            val oppgaveDto = oppgavemodellBuilder.lagOppgaveDto()
            val innsending = VaskOppgaveversjon(oppgaveDto, 0)
            "og eksternId ikke finnes fra før" - {
                "skal gi ny oppgaveversjon" {
                    val retur = transactionalManager.transaction { tx ->
                        oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(innsending, tx)
                    }

                    retur.shouldBeInstanceOf<OppgaveV3>()
                }
            }
            "og eksternId finnes fra før" - {
                val innsendingPre = NyOppgaveversjon(oppgaveDto)
                transactionalManager.transaction { tx ->
                    oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(innsendingPre, tx)
                }
                "skal oppdatere siste innkomne oppgaveversjon med data fra vaskemeldingen" {
                    val nyeFeltverdier = oppgaveDto.feltverdier.toMutableList()
                    nyeFeltverdier.add(OppgaveFeltverdiDto("aksjonspunkt", "9002"))

                    val retur = transactionalManager.transaction { tx ->
                        oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(
                            VaskOppgaveversjon(oppgaveDto.copy(feltverdier = nyeFeltverdier), 0)
                            , tx
                        )
                    }

                    retur!!.hentListeverdi("aksjonspunkt") shouldContain "9002"
                }
            }
        }
    }
}

