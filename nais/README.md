# nais

## Må settes i Vault
- `AZURE_PRIVATE_KEY_JWK`
- `AZURE_CERTIFICATE_HEX_THUMBPRINT`

## Databasemigrering

Deployflyten kjører migreringsjobben før applikasjonen deployes:

1. Byggworkflowen publiserer imaget.
2. `k9-los-api-db-migration` kjører Flyway med migreringsidentitetens `k9-los-admin`-tilgang.
3. Applikasjonen deployes bare dersom migreringsjobben fullfører.
4. Applikasjonen verifiserer Flyway-historikken ved oppstart og stopper dersom databasen ikke er oppdatert.

Den gamle applikasjonen fortsetter normalt å kjøre mens migreringen pågår. Skaler applikasjonen manuelt til 0 før migreringer som krever det på grunn av låser eller inkompatible skjemaendringer.

Lokalt og i verdikjedetester er profilen `LOCAL`. Da kjører applikasjonen selv Flyway før den starter.

Naisjobben trenger en egen Vault-identitet med tilgang til databasens `k9-los-admin`-rolle. Jobben stopper før Flyway starter dersom denne tilgangen mangler.
