# Omnia Cloud

Omnia Cloud is the independently deployable, read-only query boundary for MoneyPOS.

The first implemented slice contains only the internal member-snapshot receiver and its D1 schema.  It is not a public query service, does not contain a Cloudflare account identifier or secret, and must not be deployed with placeholder configuration.

See `docs/MoneyPOS-OMNIA-CLOUD-2-Worker-D1-Receiver-Implementation-Contract.md` for the accepted boundary and `apps/worker/README.md` for the request contract.
