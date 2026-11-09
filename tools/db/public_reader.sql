-- A read-only role for a website or Discord bot (DESIGN section 3.33). It can read Kegare's public stats and nothing else: the kegare_public
-- schema's views, and no table of Kegare's. Run it once as the database's owner, after Kegare has started against this database at least once:
--
--   psql -d kegare -v password='choose-one' -f tools/db/public_reader.sql
--
-- Then connect the website with the role kegare_public_reader. It never needs the password Kegare itself uses.
CREATE ROLE kegare_public_reader LOGIN PASSWORD :'password' NOSUPERUSER NOCREATEDB NOCREATEROLE;
GRANT USAGE ON SCHEMA kegare_public TO kegare_public_reader;
GRANT SELECT ON ALL TABLES IN SCHEMA kegare_public TO kegare_public_reader;
ALTER DEFAULT PRIVILEGES IN SCHEMA kegare_public GRANT SELECT ON TABLES TO kegare_public_reader;
REVOKE ALL ON SCHEMA kegare FROM kegare_public_reader;
REVOKE ALL ON ALL TABLES IN SCHEMA kegare FROM kegare_public_reader;
