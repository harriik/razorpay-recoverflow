-- V7: Align currency columns with the JPA String(length = 3) mappings.
ALTER TABLE payments ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE recovery_cases ALTER COLUMN currency TYPE VARCHAR(3);
