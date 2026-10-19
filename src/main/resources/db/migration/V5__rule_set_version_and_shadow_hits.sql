-- The rule file version that scored each assessment. Null for assessments made before rule files existed.
ALTER TABLE fraud_assessment ADD COLUMN rule_set_version VARCHAR(64);

-- Shadow hits are recorded but don't count toward the score. Existing hits were all enforced.
ALTER TABLE fraud_assessment_rule_hit ADD COLUMN shadow BOOLEAN NOT NULL DEFAULT FALSE;
