-- Runs once on first container start. The 'claims' database is created by POSTGRES_DB.
-- Each service owns its own database; nothing is shared across services at the SQL level.
CREATE DATABASE insights OWNER claims;
CREATE DATABASE notification OWNER claims;
