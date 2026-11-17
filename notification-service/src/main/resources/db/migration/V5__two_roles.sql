-- Staff directory uses the same sign-in role as the rest of the platform.
ALTER TABLE notification.staff_directory DROP CONSTRAINT staff_directory_role_check;
UPDATE notification.staff_directory SET role = 'CLAIMS_ADJUSTER';
ALTER TABLE notification.staff_directory ADD CONSTRAINT staff_directory_role_check CHECK (role = 'CLAIMS_ADJUSTER');
