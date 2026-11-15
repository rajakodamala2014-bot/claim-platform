-- Bank account numbers are now stored encrypted (AES-256-GCM, see platform.infrastructure.FieldCipher).
-- Ciphertext is longer than the 40-character plaintext limit. Existing plaintext rows stay readable (no prefix).
ALTER TABLE settlement.settlement RENAME COLUMN account_number TO account_number_enc;
ALTER TABLE settlement.settlement ALTER COLUMN account_number_enc TYPE TEXT;
