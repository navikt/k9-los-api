package no.nav.k9.los.nyoppgavestyring.query.mapping.transientfeltutleder

import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.los.aksjonspunktbehandling.AksjonspunktDefinisjonK9Tilbake
import no.nav.k9.los.spi.felter.*

class K9BeslutterTransientFeltutleder: TransientFeltutleder{

    override fun hentVerdi(input: HentVerdiInput): List<String> {
        val verdi = input.oppgave.hentVerdi("K9", "løsbartAksjonspunkt")
        val aksjonspunktDefBeslutter = if (input.oppgave.oppgavetype.eksternId == "k9tilbake") AksjonspunktDefinisjonK9Tilbake.FATTE_VEDTAK.kode else AksjonspunktDefinisjon.FATTER_VEDTAK.kode
        val erTilBeslutter = (verdi != null && verdi == aksjonspunktDefBeslutter).toString()
        return listOf(erTilBeslutter)
    }

    override fun where(input: WhereInput): SqlMedParams {
        val prefix: String = if (input.feltverdi as Boolean) "" else "NOT"
        val query = """
                $prefix EXISTS (
                    SELECT 1
                    FROM oppgavefelt_verdi_aktiv ov 
                    INNER JOIN oppgavefelt f ON (f.id = ov.oppgavefelt_id)
                    INNER JOIN oppgavetype ot ON (ot.id = f.oppgavetype_id)
                    INNER JOIN feltdefinisjon fd ON (fd.id = f.feltdefinisjon_id) 
                    INNER JOIN omrade fo ON (fo.id = fd.omrade_id)
                    WHERE ov.oppgave_id = o.id
                      AND fo.ekstern_id = 'K9'
                      AND fd.ekstern_id = 'løsbartAksjonspunkt'
                      AND (ot.ekstern_id in ('k9sak','k9klage') AND ov.verdi = '5016' OR ot.ekstern_id = 'k9tilbake' AND ov.verdi = '5005') 
                )
            """.trimIndent()
        return SqlMedParams(query, mapOf())
    }

    override fun orderBy(input: OrderByInput): SqlMedParams {
        val order =  if (input.økende) "ASC" else "DESC"
        val query = """
                COALESCE((
                    SELECT true
                    FROM oppgavefelt_verdi_aktiv ov 
                    INNER JOIN oppgavefelt f ON (f.id = ov.oppgavefelt_id)
                    INNER JOIN oppgavetype ot ON (ot.id = f.oppgavetype_id)
                    INNER JOIN feltdefinisjon fd ON (fd.id = f.feltdefinisjon_id) 
                    INNER JOIN omrade fo ON (fo.id = fd.omrade_id)
                    WHERE ov.oppgave_id = o.id
                      AND fo.ekstern_id = 'K9'
                      AND fd.ekstern_id = 'løsbartAksjonspunkt'
                      AND (ot.ekstern_id in ('k9sak','k9klage') AND ov.verdi = '5016' OR ot.ekstern_id = 'k9tilbake' AND ov.verdi = '5005')
                ), false) $order
            """.trimIndent()

        return SqlMedParams(query, mapOf())
    }
}