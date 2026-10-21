package no.nav.k9.los.nøkkeltall.avdelingsleder.statusfordeling

interface StatusFordeling {
    fun hentVerdi(kode6: Boolean): StatusFordelingResponse
    fun oppdaterCache(kode6: Boolean)
}
