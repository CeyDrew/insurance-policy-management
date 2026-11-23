Scripts placed here (`*.sql`, `*.sh`) run once on the first start of the Oracle container
(empty volume). Schema objects are NOT created here — they are managed by Flyway in
`backend/src/main/resources/db/migration`.
