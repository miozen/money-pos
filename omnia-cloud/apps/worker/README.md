# Worker receiver contract

`POST /internal/v1/member-snapshots:batch` is the only route in this slice.

It accepts at most 100 transformed snapshots. Headers are `X-Omnia-Key-Id`,
`X-Omnia-Timestamp` (epoch milliseconds), `X-Omnia-Nonce`, `X-Omnia-Body-SHA256`
(lower-case hexadecimal SHA-256), and `X-Omnia-Signature` (lower-case hexadecimal
HMAC-SHA-256 over `METHOD + "\\n" + PATH + "\\n" + TIMESTAMP + "\\n" + NONCE + "\\n" + BODY_HASH`).

`OMNIA_SYNC_KEYS_JSON` is a Worker secret containing a JSON object that maps a key id to a base64 HMAC secret. It is deliberately not supplied here. `sync_clients` binds a key id to one active store. A nonce is single-use for five minutes.

The request body is `{ "storeId": "...", "snapshots": [...] }`. A snapshot has
`memberId`, `version`, `operation` (`UPSERT` or `TOMBSTONE`), and `sourceUpdatedAt`.
UPSERT uses only transformed query material: `name`, `nameNormalized`, `phoneHmac`,
`phoneLast4Hmac`, `phoneMasked`, and `snapshot`. Raw `phone`, asset ledgers, and
transaction histories are rejected/not modelled. Tombstones retain only identity,
version and deletion status. Higher (or equal) versions win, so retries are safe.
