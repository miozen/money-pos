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

## Read-only personal query

`GET /` serves the mobile member-query page. `POST /api/v1/members/search` accepts
`{ "query": "..." }`, and `GET /api/v1/members/{memberId}` returns one active
member projection. Both APIs require HTTP Basic Authorization and use only a fixed
Worker-side store partition; they never accept `storeId` from a browser.

Before deploying this capability, configure these **Worker secrets/variables** outside
Git:

- `OMNIA_QUERY_PASSWORD` (secret): personal webpage password.
- `OMNIA_SEARCH_HMAC_SECRET_BASE64` (secret): the same Base64 search-HMAC value used
  by the local POS publisher. It derives full-phone and last-four lookup hashes inside
  the Worker and is never returned to the browser.
- `OMNIA_QUERY_STORE_ID` (plain Worker variable): fixed query partition. Use
  `store-01-test` only during acceptance and `store-01` for normal production use.

The search API recognizes an 11-digit full phone, a four-digit suffix, or a name
prefix. It returns at most 20 ACTIVE snapshots and emits `Cache-Control: no-store`.
It returns only the display projection, never HMAC values, normalized names or raw
phones.
