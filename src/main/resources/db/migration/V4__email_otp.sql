ALTER TABLE email_verification_tokens
    CHANGE COLUMN token_hash otp_hash VARCHAR(64) NOT NULL,
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN last_sent_at DATETIME NULL,
    ADD COLUMN send_window_started_at DATETIME NULL,
    ADD COLUMN send_count INT NOT NULL DEFAULT 0;

ALTER TABLE email_verification_tokens
    DROP INDEX uk_email_verification_hash;

UPDATE email_verification_tokens
SET used = TRUE;
