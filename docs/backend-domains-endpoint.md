# Spec — `GET /api/quiz/practitioner/domains`

> **Nota:** copia local del spec backend, mantenida en este repo Android como referencia para el cliente (`com.codecsrayo.quizgate`). La fuente canónica vive en el repo backend; los paths `src/pages/...` y `src/lib/...` en este documento se refieren a ese repo, no a este. Sincronizar manualmente cuando el backend publique cambios.

**Status:** v1, shipped 2026-05-20.
**Endpoint:** `GET /api/quiz/practitioner/domains`
**Auth:** none (public, statically prerendered).
**Cache:** `Cache-Control: public, max-age=3600`. Payload built at build-time, identical bytes per deploy.
**Source:** [src/pages/api/quiz/practitioner/domains.ts](../../src/pages/api/quiz/practitioner/domains.ts)

---

## Purpose

Canonical list of CLF-C02 exam domains used by the AWS Practitioner quiz bank. Lets clients:

- Render domain filters / dashboards without scanning `/questions`.
- Cross-reference `Question.domain` (already returned by `/questions`) with a stable label and ordering.
- Map between repo-canonical codes and the `d1..d4` short codes used by some client UIs.

Replaces the implicit contract where the client had to scan every question to discover the domain set.

---

## Response

`200 OK`, `Content-Type: application/json; charset=utf-8`.

### TypeScript schema

```ts
interface LocalizedString {
  es: string;
  en: string;
}

interface DomainEntry {
  code:   string;          // canonical, matches Question.domain — case-sensitive
  dCode:  string;          // short alias: "d1" | "d2" | "d3" | "d4"
  label:  LocalizedString; // human-readable, never null/empty
  order:  number;          // 1-indexed, matches array index + 1
}

type DomainsPayload = DomainEntry[];
```

### Example

```json
[
  { "code": "cloud-concepts", "dCode": "d1", "label": { "es": "Conceptos de la nube",   "en": "Cloud Concepts" },               "order": 1 },
  { "code": "security",       "dCode": "d2", "label": { "es": "Seguridad y cumplimiento","en": "Security & Compliance" },        "order": 2 },
  { "code": "technology",     "dCode": "d3", "label": { "es": "Tecnología en la nube",  "en": "Cloud Technology & Services" },   "order": 3 },
  { "code": "billing",        "dCode": "d4", "label": { "es": "Facturación y precios",  "en": "Billing, Pricing & Support" },    "order": 4 }
]
```

---

## Field contract

### `code` — canonical key (use this for matching)

Stable, case-sensitive. Matches the value of `Question.domain` in `/api/quiz/practitioner/questions`. This is the **only** field that should be used for joins against question data.

Current values: `"cloud-concepts"`, `"security"`, `"technology"`, `"billing"`. The set may grow if new exam domains are added; existing codes will not be renamed without a `v2` endpoint.

### `dCode` — short alias (display / legacy clients only)

Pure alias `d1..d4` in canonical order. Provided for clients whose UI uses short codes. **Do not** use `dCode` for matching against `Question.domain` — `/questions` does not emit `d1..d4`. Match by `code`, render by `dCode` if needed.

Mapping is fixed:

| `code`           | `dCode` |
|------------------|---------|
| `cloud-concepts` | `d1`    |
| `security`       | `d2`    |
| `technology`     | `d3`    |
| `billing`        | `d4`    |

### `label.es` / `label.en` — human-readable

Derived from the first occurrence of each `domain` across the real (non-synthetic) banks at build time. Fallback to a hardcoded label if no real question carries that domain. Always non-empty.

Labels are not API-stable — copy may be tweaked. Treat as display-only; never key off them.

### `order` — display ordering

1-indexed, redundant with array position (always `array.indexOf(entry) + 1`). Provided so clients that re-sort or merge entries can recover the canonical exam order without keeping it implicit.

---

## Ordering guarantee

Array order is the canonical exam order: cloud-concepts → security → technology → billing. The order field reflects this. Clients should not assume the order is alphabetical.

If a new domain is added in a future minor release, it will be appended after `billing` with `order = 5`, never inserted in the middle.

---

## Versioning & breaking changes

- **Additive changes** (new fields on `DomainEntry`, new entries appended) are non-breaking. Clients must ignore unknown fields.
- **Renames or removals** ship as `/api/quiz/practitioner/domains/v2` (path-versioned). The `v1` endpoint will remain for at least one quarter after `v2` ships.
- `code` values, once published, are immutable.

---

## Consumer guidance

### Discovery (Setup screen filters)

Fetch once on app start; cache for 1h matching the `Cache-Control` header. Display `label[locale]` to the user; key local state by `code`.

```kotlin
data class Domain(
    val code: String,
    val dCode: String,
    val label: Map<String, String>,
    val order: Int,
)

interface PractitionerApi {
    @GET("api/quiz/practitioner/domains")
    suspend fun domains(): List<Domain>
}
```

### Joining with `/questions` and `/glossary`

```kotlin
// Domain -> question count, for the Setup screen
val byDomain: Map<String, Int> = questions.groupingBy { it.domain }.eachCount()

domains.sortedBy { it.order }.forEach { d ->
    val count = byDomain[d.code] ?: 0
    render(d.label[locale]!!, count)
}
```

Same `code` is used when filtering `Question.domain` and (once curated) `GlossaryTerm.domains[]`. See sibling spec [backend-glossary-domains.md](./backend-glossary-domains.md) for the proposed extension on the glossary side.

### Locale fallback

`label.es` and `label.en` are the only two locales today. Clients targeting other locales should fall back to `en`. Do not synthesize labels from `code` — kebab-case is not user-facing copy.

---

## Failure modes

This endpoint is prerendered at build time. Failure modes are limited to:

| Failure | Symptom | Client response |
|---|---|---|
| Network error | Transport error | Retry with backoff; serve cached list if any. |
| Empty array `[]` | Build skipped real banks | Should never ship. Treat as transport error. |
| Missing `code` on an entry | Malformed payload | Skip that entry; log. |
| Unknown `code` in cached client data | Backend dropped a domain | Hide from UI; do not crash. |

No 4xx/5xx paths — the route is static. A `404` means the deploy is broken.

---

## Open items (not in v1)

- `GlossaryTerm.domains: string[]` — separate spec, depends on editorial curation pass on the 137-term bank. Ver [backend-glossary-domains.md](./backend-glossary-domains.md).
- CI validator that asserts every `code` here appears in at least one `Question.domain`. Tracked separately.
- Per-domain weight / difficulty hints — out of scope, may land in `v2`.

---

## References

- [src/pages/api/quiz/practitioner/domains.ts](../../src/pages/api/quiz/practitioner/domains.ts) — endpoint implementation.
- [src/lib/practitionerNormalize.ts](../../src/lib/practitionerNormalize.ts) — `DOMAIN_MAP` and `canonicalDomain` (source of truth for `code`).
- [src/pages/api/quiz/practitioner/questions.ts](../../src/pages/api/quiz/practitioner/questions.ts) — `/questions` endpoint that emits `Question.domain`.
- [src/pages/api/quiz/practitioner/glossary.ts](../../src/pages/api/quiz/practitioner/glossary.ts) — `/glossary` endpoint, will gain `domains[]` in a future release.
