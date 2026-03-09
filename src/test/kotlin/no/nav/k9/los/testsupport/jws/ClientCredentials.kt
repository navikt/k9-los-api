package no.nav.k9.los.testsupport.jws

import java.security.MessageDigest
import java.security.cert.CertificateFactory

object ClientCredentials {
    private val HEX_FORMAT: java.util.HexFormat = java.util.HexFormat.of()
    private fun asHexThumbprint(certificatePem : String) : String {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val certificate = certificatePem.byteInputStream().use {
            certificateFactory.generateCertificate(it)
        }
        val messageDigest = MessageDigest.getInstance("SHA-1")
        messageDigest.update(certificate.encoded)
        return HEX_FORMAT.formatHex(messageDigest.digest())
    }

    object ClientA {
        val privateKeyJwk = """
        {
            "kid": "MGBHHGXl7ITAVj-fHgBkPBQ-T5A=",
            "x5t": "MGBHHGXl7ITAVj-fHgBkPBQ-T5A=",
            "kty": "RSA",
            "n": "vTs0mS-huLVOv7_EaaIHoqkM3Rz1TOImAEVdQK6PZqqQLnbRC5yszxuqOOFPvw8QFY3HT2iUrkxlVPkW3Z9LAXS3dmZKw0MJboLHvusdmLFn0FhIgbldRyAxJ4UcepLJdcR4xofW_MgIH34xkjEDY-dSeDB4fiKi1_8lPTJYuVP5vAywfV3Z_R7msK6rlvl0g28SsOZrxJ9OC6nH3cVsT75vZcmd2eip7LLGCkO8-V9qGgAYUjocn7x6-0XlPVilCF8ic6PNClwe4bmjDR2a_SbDSc3akE8vxaMtINt49CcPfUhkPPm_0mfWsayCXzuwBfUeTaXF_ABCxkipYYpu6w",
            "e": "AQAB",
            "d": "p-V0Eca1UtFrga6AcskUxToA897RttmgpfTlfJJlIc6MBu3dJNRqb4g4TCd9PiP7PWSCRu6fnNajwfUQWKsRPcV1UlQIWZ-NKsRWvgqWQ_iEB9OM4ay6GnVxp4LvdcHvhdJA5sV39uj0bBznlrJuM6H3BjTbc-7_VW5IeDfHiQZ2pWW4DSbiwBhIUYu13IspZcsyk-fLfU-asS_lpz-Mc7XdP56xmstc9D22rOhBCz2NWpamM2UaqRS1zdn1V0wULjfO-tRMGghef_LaAuEEeOhhd-rw_wS89MfPdAoHvJWEyBVgQAS9LqxwRfrcjIu5pEf51Q0VrlV7dij8K1-KQQ",
            "p": "3auUKk-tkDtyQFr8bW_aA74hkVIBuRbnWe5F-r3O66Vy40tWHhJnGCyncCWb5f_k146ZwxwNlooheJS6T3bi4hsI5bbj3ElE_jSR_7SBVEab4bHbGkYpER4AUCHxfZwD8PJEoLZ4f14U87cBYL1GNEyZnLUitSjCDDmfJfI5LBM",
            "q": "2omKIMQetGibKTS60lMAxcVn4kQiCX3_spnrBIxLuEgXGYrOAqbsRVdSc07wKAljE-ig2SN1EaLKHyWVBxdxd4KMZqxbmh6HNuYru412ilPwZM5csLasU9TAD1yYCtju1Bj2GMU6awnc3hKoTXeWZWBrK4eubX4nb2WZxvfwXMk",
            "dp": "Eh6NTOwYZtrFGweU7Kkg6_9lpQhMBcIehRZZ-AX93Ps4KeYlku20KaC0yxD37lP9c7U_Ulh_r9d4pu-ZTxeLsim9j3FkrMP8dL79VCaAD9B5u3gbTcmAX9rQ8bvkjnzrQY28GFrx_I9HLSi_XxX5oBrGz61qud4sBm3LWYG0NKs",
            "dq": "CxnLc2ii6qUZpJkyGDbxJhql8T9mvzawQ2FAJ-X8fqriyYBcgJP8EnWiEYtj9ZSsfLlnWkBL1Q6A194v2MFfGSP_f8Onj4eXdLlyZT-FUvd6kZRN7wgIbuWyr9UTQBHO5-UwswdptUA2AO3PsMevUwz3xKlKufMbi7QMgKfdhMk",
            "qi": "vTdHKVXjTPkhe5IIzv-YxhANMIsBZVT4OFF2a0eZr06anwM-tEJCkCJTjlkQmjBqKtjbYaubTXAnX6_uTRpfNVmhhpws_hRsN6fmGBdQXwe7wSlzudrctpv-02ABRnT0EGBi9r9LSRATzh22uGwoan2xfChuUKZhy4uZqxcicbI"
        }
        """.trimIndent()

        val certificatePem = """
        -----BEGIN CERTIFICATE-----
        MIIDnjCCAoYCCQCh9N36BTiQozANBgkqhkiG9w0BAQsFADCBkDELMAkGA1UEBhMC
        Tk8xDTALBgNVBAgMBE9zbG8xDTALBgNVBAcMBE9zbG8xLzAtBgNVBAoMJk5BViAo
        QXJiZWlkcy0gb2cgdmVsZmVyZHNkaXJla3RvcmF0ZXQpMQ8wDQYDVQQLDAZOQVYg
        SVQxITAfBgNVBAMMGENsaWVudEEudW5pdC10ZXN0Lm5hdi5ubzAeFw0xOTA4MDEx
        MDIxMTZaFw0yMTA3MzExMDIxMTZaMIGQMQswCQYDVQQGEwJOTzENMAsGA1UECAwE
        T3NsbzENMAsGA1UEBwwET3NsbzEvMC0GA1UECgwmTkFWIChBcmJlaWRzLSBvZyB2
        ZWxmZXJkc2RpcmVrdG9yYXRldCkxDzANBgNVBAsMBk5BViBJVDEhMB8GA1UEAwwY
        Q2xpZW50QS51bml0LXRlc3QubmF2Lm5vMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A
        MIIBCgKCAQEAvTs0mS+huLVOv7/EaaIHoqkM3Rz1TOImAEVdQK6PZqqQLnbRC5ys
        zxuqOOFPvw8QFY3HT2iUrkxlVPkW3Z9LAXS3dmZKw0MJboLHvusdmLFn0FhIgbld
        RyAxJ4UcepLJdcR4xofW/MgIH34xkjEDY+dSeDB4fiKi1/8lPTJYuVP5vAywfV3Z
        /R7msK6rlvl0g28SsOZrxJ9OC6nH3cVsT75vZcmd2eip7LLGCkO8+V9qGgAYUjoc
        n7x6+0XlPVilCF8ic6PNClwe4bmjDR2a/SbDSc3akE8vxaMtINt49CcPfUhkPPm/
        0mfWsayCXzuwBfUeTaXF/ABCxkipYYpu6wIDAQABMA0GCSqGSIb3DQEBCwUAA4IB
        AQCWbTHF1nGfBb+9+8ZGFYSa00AwwvdzArZuFxq1IizZUpXr/ldWdHMI5Gpn+NyL
        DbkDFWTreoCRcc1UZN46yFKxNcIU8LoWe93JG6gE5Vv1sENQ1ikFLC1vtW4msli2
        Ucci3Db1xTtfdEZSfjjxtf7lIqTXxrNns2c//md0P/JFKACL5JF9iQBaXjwG/0bd
        aXCne+Bxcezh5bjcMegqDqqjAwb5/gyl8wnKowezMMcfl4HbHLLHK6bZaPN78ofl
        7mZsAoo2OlzqFUvk3XmKCmbjhxq/ZFMFz8b4upgHiCC10iv4f42bvHDsUxKux4RT
        vvEC76nH2z8IIKEr8p//znbw
        -----END CERTIFICATE-----
        """.trimIndent()

        val certificateHexThumbprint = asHexThumbprint(certificatePem)
    }
}
