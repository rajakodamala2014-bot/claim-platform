-- Hong Kong customers read Traditional Chinese. 'zh-Hant' falls back to 'zh' (Simplified), then English.
INSERT INTO notification.template (id, event_type, audience, language, subject, body) VALUES
    ('ClaimSubmitted.CUSTOMER.zh-Hant', 'ClaimSubmitted', 'CUSTOMER', 'zh-Hant',
        '我們已收到您的索償申請 {{claimNumber}}', '謝謝。我們已收到您的索償申請 {{claimNumber}}，會及時通知您最新進展。'),
    ('ClaimAssigned.CUSTOMER.zh-Hant', 'ClaimAssigned', 'CUSTOMER', 'zh-Hant',
        '{{handlerName}} 正在處理您的索償', '{{handlerName}} 正在處理您的索償 {{claimNumber}}。'),
    ('InformationRequested.CUSTOMER.zh-Hant', 'InformationRequested', 'CUSTOMER', 'zh-Hant',
        '需要您的跟進：索償 {{claimNumber}}', '請在 {{dueDate}} 前提供：{{items}}。'),
    ('ClaimApproved.CUSTOMER.zh-Hant', 'ClaimApproved', 'CUSTOMER', 'zh-Hant',
        '您的索償 {{claimNumber}} 已獲批核', '您的索償 {{claimNumber}} 已獲批核，金額為 {{amount}}。'),
    ('ClaimRejected.CUSTOMER.zh-Hant', 'ClaimRejected', 'CUSTOMER', 'zh-Hant',
        '您的索償 {{claimNumber}} 未獲批核', '原因：{{reason}}。如有新資料，您可以在應用程式中提出上訴。'),
    ('PaymentConfirmed.CUSTOMER.zh-Hant', 'PaymentConfirmed', 'CUSTOMER', 'zh-Hant',
        '已付款 {{amount}}', '{{amount}} 已存入您的帳戶（索償 {{claimNumber}}）。');

UPDATE notification.preference SET language = 'zh-Hant' WHERE user_id = 'cust-wong';
