CREATE TABLE fraud_assessment (
    transaction_id   VARCHAR(64)    NOT NULL PRIMARY KEY,
    account_id       VARCHAR(64)    NOT NULL,
    amount           DECIMAL(19, 4) NOT NULL,
    currency         VARCHAR(3)     NOT NULL,
    category         VARCHAR(32)    NOT NULL,
    merchant         VARCHAR(128),
    country          VARCHAR(2)     NOT NULL,
    transaction_time TIMESTAMP WITH TIME ZONE NOT NULL,
    risk_score       INT            NOT NULL,
    flagged          BOOLEAN        NOT NULL,
    evaluated_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_assessment_account_time ON fraud_assessment (account_id, transaction_time);
CREATE INDEX idx_assessment_flagged_time ON fraud_assessment (flagged, transaction_time);

CREATE TABLE fraud_assessment_rule_hit (
    transaction_id VARCHAR(64)  NOT NULL REFERENCES fraud_assessment (transaction_id),
    rule_code      VARCHAR(48)  NOT NULL,
    description    VARCHAR(255) NOT NULL,
    score          INT          NOT NULL
);

CREATE INDEX idx_rule_hit_transaction ON fraud_assessment_rule_hit (transaction_id);
