# ADR-0002: Pluggable AI provider registry (BYOK + free tier + on-device)

**Status:** Accepted (2026-07-20)

## Context

The rewriting brain must come from somewhere, and every choice has a bill or
a limitation attached: cloud APIs cost per token, free tiers have quotas,
on-device models have limited device support and weaker persona fidelity.
Competitors solve this by running their own proxy backend and charging a
subscription. We don't want to run a server for a fun showcase project.

## Decision

One interface, many brains:

```kotlin
interface CompletionProvider {
    suspend fun rewrite(system: String, text: String): Result<String>
}
```

Registered implementations at launch: **Gemini free tier** (default — works
out of the box with a free key), **Anthropic / OpenAI / OpenRouter** via
bring-your-own-key. **On-device** (Gemini Nano via AICore; llama.cpp
fallback under evaluation) joins in Phase 3 and completes the hybrid story.

Routing policy sits above the registry: quick tone tweaks go to the
cheap/local provider, full persona rewrites to the configured "quality"
provider. Keys live in Android Keystore-backed storage and never leave the
device except to the provider the user chose.

## Because

- **No server = no billing liability, no privacy liability, no ops.** The
  user's relationship is with their provider; ours is with their good taste
  in personas.
- BYOK + a free default covers everyone from "I have an Anthropic key" to
  "I will not pay a cent"; on-device later covers "I will not send a byte."
- The interface is small enough that adding a provider is a beginner-friendly
  contribution, and honest enough that golden/contract tests can pin
  behavior per provider.
- It's the same abstraction a hosted-key paid tier would slot into
  (`HostedProvider` behind the same interface) if traction ever justifies
  one — see GTM.md money notes.

## Consequences

- Onboarding must explain "get a free Gemini key" in under a minute or we
  lose everyone at the door. This is a docs problem as much as a UX one.
- Provider differences (rate limits, refusals, latency) must surface as
  polite in-voice errors, not stack traces. Error mapping is part of each
  provider's contract tests.
- We accept N HTTP client implementations' worth of maintenance in exchange
  for zero servers. The registry pattern keeps N additive, not
  combinatorial.
