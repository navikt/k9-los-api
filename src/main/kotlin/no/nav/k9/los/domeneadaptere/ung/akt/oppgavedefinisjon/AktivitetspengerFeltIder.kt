package no.nav.k9.los.domeneadaptere.ung.akt.oppgavedefinisjon

object AktivitetspengerFeltIder {
    object Behandling {
        const val UUID = "behandlingUuid"
        const val TYPEKODE = "behandlingTypekode"
        const val STATUS = "behandlingsstatus"
        const val STEG = "behandlingssteg"
        const val ARSAK = "behandlingsårsak"
        const val RESULTATTYPE = "resultattype"
    }

    object Soknad {
        const val NYE_KRAV = "nyeKrav"
    }

    object Sak {
        const val AKTOR_ID = "aktorId"
        const val FAGSYSTEM = "fagsystem"
        const val SAKSNUMMER = "saksnummer"
        const val MOTTATT_DATO = "mottattDato"
        const val TID_SIDEN_MOTTATT_DATO = "tidSidenMottattDato"
        const val REGISTRERT_DATO = "registrertDato"
    }

    object Vedtak {
        const val DATO = "vedtaksdato"
        const val YTELSESTYPE = "ytelsestype"
        const val BEHANDLENDE_ENHET = "behandlendeEnhet"
        const val TOTRINNSKONTROLL = "totrinnskontroll"
    }

    object Aksjonspunkt {
        const val ALLE = "aksjonspunkt"
        const val AKTIVE = "aktivtAksjonspunkt"
        const val LOSBART = "løsbartAksjonspunkt"
        const val UTFORT = "utførtAksjonspunkt"
        const val AVBRUTT = "avbruttAksjonspunkt"
        const val FREMTIDIG = "fremtidigAksjonspunkt"
    }

    object Saksbehandling {
        const val ANSVARLIG_SAKSBEHANDLER = "ansvarligSaksbehandler"
        const val ANSVARLIG_BESLUTTER = "ansvarligBeslutter"
        const val LIGGER_HOS_BESLUTTER = "liggerHosBeslutter"
        const val TID_FORSTE_GANG_HOS_BESLUTTER = "tidFørsteGangHosBeslutter"
    }

    object Ventetid {
        const val AKTIV_ARSAK = "aktivVenteårsak"
        const val AKTIV_FRIST = "aktivVentefrist"
    }
}