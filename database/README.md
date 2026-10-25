# PostgreSQL schema reference

`01_schema.sql` reproduces the nine-table SQL embedded in the supplied specification. The executable source is `backend/src/main/resources/db/migration/V1__initial_schema.sql`.

Create the database outside Flyway, then let the application apply migrations. Do not run this reference SQL first and do not edit both copies independently. Keep applied migrations immutable; add schema changes after P3 as V4 and later migrations. No seed data or application users are inserted in P1.

`02_demo_seed.sql` is an explicit opt-in P3 sample, never a startup migration. Run only against a dedicated demo database after Flyway migrations. Vehicles use `DEMO-P3-*` codes and `[DEMO P3]` names. No accounts or passwords are inserted. Initial trips depart seven days after the first execution; reruns reuse the existing demo trips without changing their dates. Demo vehicle booking history makes the seed abort instead of modifying its seat layout. Seed does not update/delete existing data; do not use reserved demo codes for real vehicles.

The P3 verification script applies the seed twice on its isolated database and checks that public search still returns exactly one matching bus trip.
