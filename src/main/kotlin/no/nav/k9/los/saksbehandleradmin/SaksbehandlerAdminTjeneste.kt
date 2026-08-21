package no.nav.k9.los.saksbehandleradmin

import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstMedOmråde
import no.nav.k9.los.ko.db.OppgaveKoRepository
import no.nav.k9.los.lagretsok.LagretSøkTjeneste
import no.nav.k9.los.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.uttrekk.UttrekkTjeneste
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

class SaksbehandlerAdminTjeneste(
    private val transactionalManager: TransactionalManager,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val oppgaveKøV3Repository: OppgaveKoRepository,
    private val lagretSøkTjeneste: LagretSøkTjeneste,
    private val uttrekkTjeneste: UttrekkTjeneste,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste
) {

    // TODO: slett når frontend har begynt å bruke nytt endepunkt
    suspend fun søkSaksbehandler(epostDto: EpostDto, område: Områder, skjermet: Boolean): Saksbehandler {
        var saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedEpost(epostDto.epost, skjermet)
        if (saksbehandler == null) {
            saksbehandler = Saksbehandler(
                null, null, null, epostDto.epost, null, listOf(område)
            )
            saksbehandlerRepository.addSaksbehandler(epostDto.epost, område)
        }
        return saksbehandler
    }

    suspend fun leggTilSaksbehandlerForEpost(epost: String, område: Områder) {
        // lagrer med tomme verdier, disse blir populert etter at saksbehandleren har logget seg inn
        saksbehandlerRepository.addSaksbehandler(epost, område)
    }

    suspend fun slettSaksbehandlerForId(id: Long, brukerkontekst: BrukerkontekstMedOmråde) {
        val skjermet = brukerkontekst.harTilgangTilKode6

        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedId(id)

        val lagredeSøk = lagretSøkTjeneste.hentAlle(saksbehandler!!.navident!!, skjermet)
        lagredeSøk.forEach {
            lagretSøkTjeneste.slett(saksbehandler.navident!!, it.id!!, skjermet)
        }

        transactionalManager.transaction { tx ->
            // V3-modellen: Fjerner saksbehandler fra køer i alle områder, siden selve
            // saksbehandlerraden slettes under og ellers ville etterlatt dinglende koblinger.
            Områder.entries.forEach { område ->
                oppgaveKøV3Repository.hentKoerMedOppgittSaksbehandler(tx, saksbehandler.id!!, skjermet, true, område)
                    .forEach { kø ->
                        oppgaveKøV3Repository.endre(
                            tx,
                            kø.copy(saksbehandlerIds = kø.saksbehandlerIds - saksbehandler.id!!),
                            skjermet,
                            område
                        )
                    }
            }

            // Sletter fra saksbehandler-tabellen
            saksbehandlerRepository.slettSaksbehandlerForId(tx, id, skjermet)
        }
    }

    suspend fun slettSaksbehandler(
        epost: String,
        område: Områder,
        brukerkontekst: BrukerkontekstMedOmråde,
    ) {
        val skjermet = brukerkontekst.harTilgangTilKode6

        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedEpost(epost, skjermet) ?: throw IllegalStateException("Kunne ikke finne saksbehandler med epost")
        if (!saksbehandler.områder.contains(område)) {
            throw IllegalStateException("Saksbehandler med epost $epost har ikke område ${område.eksternId}")
        }

        if (saksbehandler.områder.size > 1) {
            transactionalManager.transaction { tx ->
                saksbehandlerRepository.fjernOmrådeFraSaksbehandler(tx, epost, skjermet, område)
                //TODO: fjern saksbehandler-områdets reservasjoner
            }
            return
        }

        if (saksbehandler.navident != null) {
            val lagredeSøk = lagretSøkTjeneste.hentAlle(saksbehandler.navident!!, skjermet)
            lagredeSøk.forEach {
                lagretSøkTjeneste.slett(saksbehandler.navident!!, it.id!!, skjermet)
            }
            val uttrekkeneTilSakbehandler = uttrekkTjeneste.hentForSaksbehandler(saksbehandler.id!!)
            uttrekkeneTilSakbehandler.forEach {
                uttrekkTjeneste.slett(it.id!!)
            }
        }

        transactionalManager.transaction { tx ->
            // V3-modellen: Fjerner saksbehandler fra køer i alle områder, siden selve
            // saksbehandlerraden slettes under og ellers ville etterlatt dinglende koblinger.
            Områder.entries.forEach { områdeForKø ->
                oppgaveKøV3Repository.hentKoerMedOppgittSaksbehandler(tx, saksbehandler.id!!, skjermet, true, områdeForKø)
                    .forEach { kø ->
                        oppgaveKøV3Repository.endre(
                            tx,
                            kø.copy(saksbehandlere = kø.saksbehandlere - epost),
                            skjermet,
                            områdeForKø
                        )
                    }
            }

            // Sletter fra saksbehandler-tabellen
            saksbehandlerRepository.slettSaksbehandler(
                tx,
                epost,
                skjermet
            )
        }
    }

    suspend fun hentSaksbehandlere(område: Områder, skjermet: Boolean): List<SaksbehandlerDto> {
        return transactionalManager.transactionSuspend { tx ->
            val saksbehandlere = saksbehandlerRepository.hentAlleSaksbehandlere(tx, område, skjermet)
            val saksbehandlerIder = saksbehandlere.map { it.id!! }.toSet()
            val antallReservasjoner = reservasjonV3Tjeneste.tellReservasjonerForSaksbehandlere(saksbehandlerIder, tx)

            saksbehandlere.map {
                SaksbehandlerDto(
                    id = it.id,
                    brukerIdent = it.navident,
                    navn = it.navn,
                    epost = it.epost,
                    enhet = it.enhet,
                    antallAktiveReservasjoner = antallReservasjoner.getOrElse(it.id!!) { 0 }
                )
            }.sortedBy { it.navn }
        }
    }
}
