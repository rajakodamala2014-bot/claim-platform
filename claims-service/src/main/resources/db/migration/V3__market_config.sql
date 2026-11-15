-- The six launch markets. Adding a market is a data change (plus authority limits), not a code change.
INSERT INTO platform.market_config
    (market_code, name, currency, timezone, sla_hours, info_request_due_days, cooling_off_days, languages)
VALUES
    ('SG', 'Singapore', 'SGD', 'Asia/Singapore',    48, 14, 14, ARRAY['en', 'zh']),
    ('HK', 'Hong Kong', 'HKD', 'Asia/Hong_Kong',    48, 14, 14, ARRAY['en', 'zh-HK']),
    ('MY', 'Malaysia',  'MYR', 'Asia/Kuala_Lumpur', 48, 14, 14, ARRAY['en', 'ms']),
    ('TH', 'Thailand',  'THB', 'Asia/Bangkok',      72, 14, 14, ARRAY['th', 'en']),
    ('ID', 'Indonesia', 'IDR', 'Asia/Jakarta',      72, 14, 14, ARRAY['id', 'en']),
    ('AU', 'Australia', 'AUD', 'Australia/Sydney',  48, 14, 14, ARRAY['en']);
