package no.nav.k9.los.oppgavedefinisjon.omraade

/**
 * Noe som identifiserer et område, dvs. bærer en område-eksternId.
 *
 * Finnes i tre varianter:
 * - [Områder] — områdene produksjonskoden kjenner på kompileringstidspunktet. Produksjonskode skal
 *   normalt bruke denne typen direkte, slik at `when`-uttrykk er uttømmende og nye områder ikke kan
 *   snike seg inn.
 * - [Område] — den persisterte raden i tabellen `omrade`, med database-id.
 * - Ad hoc-områder i test, som lager sitt eget isolerte område uten at det finnes i [Områder].
 *
 * Grensesnittet er bevisst minimalt, og finnes kun for de få stedene som må kunne bære et område som
 * ikke nødvendigvis er kjent på kompileringstidspunktet (se `OppgaveDtoType`). Alt annet skal
 * fortsatt typebindes til [Områder].
 */
interface Områdeidentifikator {
    val eksternId: String
}

