# Spec — `GlossaryTerm.domains[]` field

> **Nota:** copia local del spec backend, mantenida en este repo Android como referencia para el cliente (`com.codecsrayo.quizgate`). La fuente canónica vive en el repo backend; los paths `src/data/...` y `src/pages/...` en este documento se refieren a ese repo, no a este. Sincronizar manualmente cuando el backend publique cambios.

**Status:** v1 field shipped 2026-05-20 on existing `GET /api/quiz/practitioner/glossary`.
**Endpoint:** `GET /api/quiz/practitioner/glossary` (unchanged URL, additive field).
**Source:** [src/pages/api/quiz/practitioner/glossary.ts](../../src/pages/api/quiz/practitioner/glossary.ts)

---

## What changed

Every entry in the `/glossary` payload now carries a `domains: string[]` field. The field is **always present** and **always an array** — never `null`, never absent.

Today the array is empty for every term. Population happens through a separate editorial pass; clients can ship the consumer logic now and benefit from it incrementally as terms get curated.

```diff
 {
   "symbol": "EC2",
   "name": "Elastic Compute Cloud",
   "category": "compute",
+  "domains": [],
   "desc": { "es": "...", "en": "..." }
 }
```

---

## Semantics

- Each string in `domains` is a canonical CLF-C02 domain code, matching `Question.domain` and `DomainEntry.code` from [`/domains`](./backend-domains-endpoint.md).
- Valid values: `"cloud-concepts" | "security" | "technology" | "billing"`.
- A term may belong to multiple domains (e.g. IAM → security + technology). The array is the natural fit.
- `[]` is the documented "neutral" state — the term applies to no specific exam domain. Consumers should treat neutral terms as members of the uniform pool only, never of any domain-targeted pool.

## Validation

Endpoint filters each entry's `domains` against the canonical set at request time:

- Unknown codes are silently dropped (defense against typos in source data).
- Non-string entries are dropped.
- A malformed `domains` value (not an array) is replaced with `[]`.

This is a soft guard. The hard CI assertion ("every code in glossary is present in at least one `Question.domain`") is still open — tracked separately, not in this release.

## Consumer pattern (Android)

```kotlin
data class GlossaryTerm(
    val symbol: String,
    val name: String,
    val category: String,
    val domains: List<String> = emptyList(),   // tolerate older builds
    val desc: Map<String, String>,
    // ... other fields
)

// In the push worker:
val failingDomains: Set<String> = stats.entries
    .filter { (_, s) -> s.shown >= 3 && s.wrong.toFloat() / s.shown > 0.5f }
    .mapNotNull { questionsById[it.key]?.domain }
    .toSet()

val biased = terms.filter { it.domains.any(failingDomains::contains) }

val chosen = if (biased.isNotEmpty() && Random.nextFloat() < BIAS_RATIO) {
    biased.random()
} else {
    terms.random()
}
```

The `shown >= 3` gate avoids treating a single early wrong answer as a domain weakness. Adjust threshold or swap for a Wilson lower-bound if tighter calibration is needed.

`BIAS_RATIO` starts at `0.7f` (70% bias, 30% uniform exploration) per the original proposal.

## Compatibility

- **Forward** — clients that don't know about `domains` ignore the field; payload is otherwise unchanged.
- **Backward** — clients on older builds that expected `domains` to be optional remain compatible because the parser default is `emptyList()`.
- **Breaking changes** — any future rename or removal ships as `/api/quiz/practitioner/glossary/v2`.

## Curation status

- 137 terms in the bank, all currently `domains: []`.
- Editorial pass deferred; tracked in `.harness/` once initialized.
- Until curation lands, the client behavior degrades gracefully to the prior uniform random pick — no regression.

## References

- [backend-domains-endpoint.md](./backend-domains-endpoint.md) — sibling spec for `/domains` endpoint (source of canonical codes).
- [src/data/quiz_center/types.ts](../../src/data/quiz_center/types.ts) — `AwsGlossaryTerm` type with `domains?: string[]`.
- [src/data/quiz_center/aws/practitioner/data_sintetic/glossary/aws_glossary.ts](../../src/data/quiz_center/aws/practitioner/data_sintetic/glossary/aws_glossary.ts) — source data, awaiting curation.
