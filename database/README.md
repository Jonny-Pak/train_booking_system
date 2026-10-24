# PostgreSQL schema reference

`01_schema.sql` reproduces the nine-table SQL embedded in the supplied specification. The executable source is `backend/src/main/resources/db/migration/V1__initial_schema.sql`.

Create the database outside Flyway, then let the application apply migrations. Do not run this reference SQL first and do not edit both copies independently. Add future schema changes as Flyway V2/V3 migrations. No seed data or application users are inserted in P1.
