# Omnia Cloud

Omnia Cloud is the independently deployable, read-only query boundary for MoneyPOS.

The first implemented slices contain the internal member-snapshot receiver, its D1 schema, and a small password-protected, read-only member query page. It does not contain a Cloudflare account identifier or secret, and must not be deployed with placeholder configuration.

See `docs/MoneyPOS-OMNIA-CLOUD-8-Read-Only-Member-Query-Implementation-Contract.md` for the query boundary and `apps/worker/README.md` for endpoint/configuration contracts.
