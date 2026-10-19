-- Accepted currencies and their rate to the base currency (fraud.base-currency, ZAR by default).
-- A transaction is accepted only while its currency has a row here. Maintained by hand, no redeploy needed:
--   INSERT INTO exchange_rate (currency, rate_to_base) VALUES ('USD', 18.50)
--   ON CONFLICT (currency) DO UPDATE SET rate_to_base = EXCLUDED.rate_to_base, updated_at = now();
--   DELETE FROM exchange_rate WHERE currency = 'USD';   -- stops accepting USD
-- Each assessment stores the rate it was scored with, so later rate changes don't alter history.
CREATE TABLE exchange_rate (
    currency     VARCHAR(3)               NOT NULL PRIMARY KEY CHECK (currency ~ '^[A-Z]{3}$'),
    rate_to_base NUMERIC(19, 8)           NOT NULL CHECK (rate_to_base > 0),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

INSERT INTO exchange_rate (currency, rate_to_base) VALUES ('ZAR', 1);
