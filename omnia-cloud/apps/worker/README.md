# Worker receiver contract

`POST /internal/v1/member-snapshots:batch` receives member snapshots. `POST
/internal/v1/member-snapshots:test` accepts the same signed `{ "storeId": "..." }`
body and verifies the Worker/D1 connection without writing a snapshot.

It accepts at most 100 transformed snapshots. Headers are `X-Omnia-Timestamp`
(epoch milliseconds), `X-Omnia-Nonce`, `X-Omnia-Body-SHA256`
(lower-case hexadecimal SHA-256), and `X-Omnia-Signature` (lower-case hexadecimal
HMAC-SHA-256 over `METHOD + "\\n" + PATH + "\\n" + TIMESTAMP + "\\n" + NONCE + "\\n" + BODY_HASH`).

`OMNIA_SYNC_STORE_SECRETS_JSON` is a Worker secret mapping each `storeId` to one Base64 HMAC secret or a short current/previous-secret array. It is deliberately not supplied here. A nonce is single-use for five minutes.

The request body is `{ "storeId": "...", "snapshots": [...] }`. A snapshot has
`memberId`, `version`, `operation` (`UPSERT` or `TOMBSTONE`), and `sourceUpdatedAt`.
UPSERT uses only transformed query material: `name`, `nameNormalized`, `phoneHmac`,
`phoneLast4Hmac`, `phoneMasked`, and `snapshot`. Raw `phone`, asset ledgers, and
transaction histories are rejected/not modelled. Tombstones retain only identity,
version and deletion status. Higher (or equal) versions win, so retries are safe.
The Worker claims a nonce, then writes every snapshot and the audit row in one D1
batch; a write failure returns an error rather than a successful acknowledgement.
