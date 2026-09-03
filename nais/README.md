# nais

## Må settes i Vault
- `AZURE_PRIVATE_KEY_JWK`
- `AZURE_CERTIFICATE_HEX_THUMBPRINT`

## Databasemigrering

Applikasjonen kjører ikke Flyway ved oppstart. Migrering og deploy er separate, manuelle operasjoner:

1. Kjør workflowen `Migrer database` med image-tag og miljø.
2. La den gamle applikasjonen kjøre mens migreringen pågår når migreringen er bakoverkompatibel.
3. Skaler applikasjonen manuelt til 0 før migreringer som krever det på grunn av låser eller inkompatible skjemaendringer.
4. Kjør workflowen `Deploy applikasjon` med samme image-tag og miljø. Workflowen validerer Flyway-historikken før applikasjonen deployes.

Begge workflowene bruker samme concurrency-gruppe per miljø. En appdeploy kan derfor ikke verifisere historikken samtidig som en migreringsworkflow kjører.

Naisjobben heter `k9-los-api-db-migration` og trenger en egen Vault-identitet med tilgang til databasens `k9-los-admin`-rolle. Jobben stopper før Flyway starter dersom denne tilgangen mangler.
