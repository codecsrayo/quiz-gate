# Requerimiento backend — vincular glosario con dominios de quiz

**Estado:** propuesta, pendiente de revisión backend.
**Endpoint afectado:** `GET /api/quiz/practitioner/glossary`
**Endpoint relacionado (ya en producción v1, 2026-05-20):** `GET /api/quiz/practitioner/domains` — define el vocabulario canónico de `code`. Esta propuesta lo extiende a `GlossaryTerm`.
**Cliente Android:** `com.codecsrayo.quizgate` — habilitará el feature "refuerzo por notificación" cuando los datos estén poblados.

---

## Motivación

Hoy `GlossaryPushWorker` elige términos con `random()` uniforme, sin tener en cuenta el desempeño del usuario en el quiz. Queremos que las notificaciones del glosario refuercen específicamente los dominios donde el usuario falla más.

Para hacer ese cruce en cliente necesitamos un campo explícito en cada `GlossaryTerm` que apunte al mismo vocabulario de dominios que ya emite `Question.domain` y publica `/api/quiz/practitioner/domains` como `code` (kebab-case: `"cloud-concepts"`, `"security"`, `"technology"`, `"billing"`). Sin ese contrato, el cliente queda atado a string-matching frágil contra `category` o `tags`.

---

## Cambio principal — `GlossaryTerm`

Añadir el campo `domains: string[]` con los códigos canónicos del dominio del examen (los mismos que ya publica `/domains.code` y `Question.domain`).

### Diff de schema

```diff
 {
   "symbol": "EC2",
   "name": "Elastic Compute Cloud",
-  "category": "Compute",
+  "category": "Compute",                          // se preserva (taxonomía libre)
+  "domains": ["technology", "security"],          // NUEVO: códigos canónicos
   "synthetic": false,
   "desc": { "es": "...", "en": "..." },
   "aliases": ["..."],
   "tags": ["..."],
   "options": { "es": [...], "en": [...] }
 }
```

### Reglas

- `domains` es un **array** (un término puede pertenecer a varios dominios — ej. IAM ↔ `"security"` + cualquier otro relevante).
- Si un término no aplica a ningún dominio del examen → `"domains": []`. El cliente lo trata como "neutral" y entra al pool uniforme.
- El cliente debe tolerar la **ausencia** del campo durante la transición (fallback a `[]`).
- **Source of truth de los códigos:** `/api/quiz/practitioner/domains` (campo `code`). Inmutables una vez publicados, case-sensitive, kebab-case. Hoy: `"cloud-concepts"`, `"security"`, `"technology"`, `"billing"`.
- **No usar `dCode`** (`d1..d4`) en este campo — `dCode` es un alias de presentación para clientes legacy, no un identificador para joins.

### Por qué no reutilizar `category` / `tags`

- `category` es taxonomía editorial (`"Compute"`, `"Storage"`) — útil para futuros agrupadores de UI, no representa el mapeo al curriculum del examen. Mantenerlos separados evita acoplar dos conceptos distintos en un solo campo.
- `tags` es texto libre — usable como heurística secundaria, no como contrato.

---

## Relación con `/api/quiz/practitioner/domains`

`/domains` ya está en producción (v1, shipped 2026-05-20) y define el vocabulario canónico. Este requerimiento **no toca `/domains`** — solo extiende `GlossaryTerm` con un campo que referencia los mismos `code`. Cualquier valor que aparezca en `GlossaryTerm.domains[]` debe existir como `code` en `/domains`.

Validación recomendada en build-time del backend: un check de CI que falle si un término referencia un `code` que no está en `/domains`. Análogo al "open item" ya listado en el spec de `/domains` para `Question.domain`.

---

## Migración

1. **Backend** añade `domains: []` (array vacío) a todos los registros existentes de `/glossary`. Despliegue retro-compatible.
2. **Curaduría editorial** completa el array por término, usando los `code` de `/domains` como valores válidos. Puede hacerse incremental — los términos con `[]` siguen funcionando con random uniforme.
3. **Cliente Android** lanza el feature "refuerzo por notificación" leyendo el campo. Con `[]` mantiene el comportamiento actual. Compatibilidad total.

---

## Uso esperado en cliente (pseudocódigo)

```kotlin
// En GlossaryPushWorker.doWork(), tras cargar terms y stats:
val questionsById = quizRepo.loadCached().associateBy { it.id }

val failingDomains: Set<String> = stats.entries
    .filter { (_, s) -> s.shown > 0 && s.wrong.toFloat() / s.shown > 0.5f }
    .mapNotNull { questionsById[it.key]?.domain }
    .toSet()

val biased = terms.filter { it.domains.any(failingDomains::contains) }

val chosen = if (biased.isNotEmpty() && Random.nextFloat() < BIAS_RATIO) {
    biased.random()
} else {
    terms.random()  // pool uniforme, comportamiento actual
}
```

`BIAS_RATIO` arranca en `0.7f` (70% sesgo a debilidades, 30% exploración uniforme para no encerrar al usuario en un solo tema). Ajustable vía Prefs si se quiere exponer al usuario.

---

## Campos extra considerados y descartados

| Campo | Propuesta | Decisión | Razón |
|---|---|---|---|
| `priority: int` por término | Ordenar dentro de un dominio | Descartado | Sesgo por `failureRate` ya prioriza implícitamente; añadir manual genera trabajo editorial sin beneficio claro. |
| `relatedQuestionIds: string[]` | Vincular término ↔ preguntas específicas | Descartado | Mantenimiento caro (cada pregunta nueva exige curar términos). `domains[]` da 90% del beneficio con 10% del costo. |
| `difficulty: int` por término | Filtrar términos avanzados | Descartado | Fuera de scope para esta iteración. Reabrir si surge necesidad. |

---

## Checklist de aceptación

- [ ] `/glossary` devuelve `domains: string[]` en todos los registros (vacío o poblado).
- [ ] Todo `code` en `GlossaryTerm.domains[]` existe también como entrada en `/api/quiz/practitioner/domains` (campo `code`).
- [ ] Cliente actual (pre-feature) no rompe ante el campo nuevo — verificado contra parser tolerante en `GlossaryTerm.kt`.
- [ ] CI valida la integridad referencial entre `/glossary` y `/domains` (opcional pero recomendado).

---

## Referencias en el repo cliente

- [GlossaryTerm.kt](../app/src/main/java/com/codecsrayo/quizgate/GlossaryTerm.kt) — modelo a extender con `domains`.
- [GlossaryPushWorker.kt](../app/src/main/java/com/codecsrayo/quizgate/GlossaryPushWorker.kt) — donde aterriza la lógica de sesgo.
- [Question.kt](../app/src/main/java/com/codecsrayo/quizgate/Question.kt) — fuente del vocabulario de `domain` codes.
- [QuizSelector.kt](../app/src/main/java/com/codecsrayo/quizgate/QuizSelector.kt) — referencia del patrón "stats → selección ponderada" que se replica en el worker.
