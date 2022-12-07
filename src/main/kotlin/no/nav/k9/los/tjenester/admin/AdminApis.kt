package no.nav.k9.los.tjenester.admin

import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.routing.Route

fun Route.AdminApis() {
    @Location("/admin/synkroniseroppgave")
    class synkroniserOppgave

    get { _: synkroniserOppgave ->
    }

    @Location("/admin/sepaaoppgave")
    class hentOppgave

    get { _: hentOppgave ->
    }

    @Location("/admin/sepaaeventer")
    class hentEventlogg

    get { _: hentEventlogg ->
    }

    @Location("/admin/oppdateringavoppgave")
    class oppdaterOppgave

    get { _: oppdaterOppgave ->
    }

    @Location("/admin/prosesser-melding")
    class prosesserMelding

    get { _: prosesserMelding ->
    }

    @Location("/admin/hent-alle-oppgaver-knyttet-til-behandling")
    class hentAlleOppgaverForBehandling

    get { _: hentAlleOppgaverForBehandling ->
    }

    @Location("/admin/deaktiver-oppgave")
    class deaktiverOppgave

    get { _: deaktiverOppgave ->
    }

    @Location("/admin/aktiver-oppgave")
    class aktiverOppgave

    get { _: aktiverOppgave ->
    }
}
