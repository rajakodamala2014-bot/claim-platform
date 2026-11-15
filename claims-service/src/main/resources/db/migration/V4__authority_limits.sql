-- Authority limits per role / market / line of business, in the market currency (minor units).
-- Roughly equivalent across markets: handler ~ SGD 10k, team lead ~ SGD 50k, manager ~ SGD 500k.
-- Changing a limit is a data change; decisions above the proposer's limit go to a team lead for approval.
INSERT INTO handling.authority_limit (id, role, market_code, line_of_business, max_amount_minor, currency)
SELECT gen_random_uuid(), r.role, m.market_code, l.lob, r.factor * m.handler_limit_minor, m.currency
FROM (VALUES ('SG', 'SGD', 1000000::BIGINT),          -- SGD     10,000.00
             ('HK', 'HKD', 6000000::BIGINT),          -- HKD     60,000.00
             ('MY', 'MYR', 3000000::BIGINT),          -- MYR     30,000.00
             ('TH', 'THB', 25000000::BIGINT),         -- THB    250,000.00
             ('ID', 'IDR', 10000000000::BIGINT),      -- IDR 100,000,000.00
             ('AU', 'AUD', 1000000::BIGINT))          -- AUD     10,000.00
         AS m(market_code, currency, handler_limit_minor)
CROSS JOIN (VALUES ('HANDLER', 1), ('TEAM_LEAD', 5), ('MANAGER', 50)) AS r(role, factor)
CROSS JOIN (VALUES ('MOTOR'), ('PROPERTY')) AS l(lob);
