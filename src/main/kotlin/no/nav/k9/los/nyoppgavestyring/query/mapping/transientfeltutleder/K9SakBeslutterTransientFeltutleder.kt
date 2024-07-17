package no.nav.k9.los.nyoppgavestyring.query.mapping.transientfeltutleder

import no.nav.k9.los.spi.felter.*

class K9SakBeslutterTransientFeltutleder: TransientFeltutleder{

    override fun hentVerdi(input: HentVerdiInput): List<String> {
        val verdi = input.oppgave.hentVerdi("K9", "løsbartAksjonspunkt")
        val erTilBeslutter = (verdi != null && verdi == "5016").toString()
        return listOf(erTilBeslutter)
    }

    override fun where(input: WhereInput): SqlMedParams {
        val prefix: String = if (input.feltverdi == "true") "" else "NOT"
        val query = """
                $prefix EXISTS (
                    SELECT 'Y'
                    FROM Oppgavefelt_verdi_aktiv ov 
                    INNER JOIN Oppgavefelt f ON (f.id = ov.oppgavefelt_id) 
                    INNER JOIN Feltdefinisjon fd ON (fd.id = f.feltdefinisjon_id) 
                    INNER JOIN Omrade fo ON (fo.id = fd.omrade_id)
                    WHERE ov.oppgave_id = o.id
                      AND fo.ekstern_id = 'K9'
                      AND fd.ekstern_id = 'løsbartAksjonspunkt'
                      AND ov.verdi = '5016'
                )
            """.trimIndent()
        return SqlMedParams(query, mapOf())
    }

    override fun orderBy(input: OrderByInput): SqlMedParams {
        val order =  if (input.økende) "ASC" else "DESC"
        val query = """
                COALESCE((
                    SELECT true
                    FROM Oppgavefelt_verdi_aktiv ov 
                    INNER JOIN Oppgavefelt f ON (f.id = ov.oppgavefelt_id) 
                    INNER JOIN Feltdefinisjon fd ON (fd.id = f.feltdefinisjon_id) 
                    INNER JOIN Omrade fo ON (fo.id = fd.omrade_id)
                    WHERE ov.oppgave_id = o.id
                      AND fo.ekstern_id = 'K9'
                      AND fd.ekstern_id = 'løsbartAksjonspunkt'
                      AND ov.verdi = '5016'
                ), false) $order
            """.trimIndent()

        return SqlMedParams(query, mapOf())
    }
}