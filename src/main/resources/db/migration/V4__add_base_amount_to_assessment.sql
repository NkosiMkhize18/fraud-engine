-- Every assessment stored before this migration was in ZAR, the base currency, so its base amount is its amount.
ALTER TABLE fraud_assessment ADD COLUMN base_amount  DECIMAL(19, 4);
ALTER TABLE fraud_assessment ADD COLUMN rate_to_base NUMERIC(19, 8);

UPDATE fraud_assessment SET base_amount = amount, rate_to_base = 1;

ALTER TABLE fraud_assessment ALTER COLUMN base_amount  SET NOT NULL;
ALTER TABLE fraud_assessment ALTER COLUMN rate_to_base SET NOT NULL;
