# FreeFlow Android

Android client for the FreeFlow DNS-based covert messaging protocol. Designed for last-resort text communication during internet blackouts in censored regions.

## Protocol

Implements FreeFlow Protocol v2.1:

- **Transport:** DNS AAAA queries and responses over UDP port 53, with HTTP relay fallback
- **Encoding:** Proquint (CVCVC words for censored networks) and hex (uncensored)
- **Session:** 4-query HELLO handshake with ephemeral X25519 ECDH, HKDF-SHA256 session keys
- **Identity:** X25519 key pairs, SHA-256 fingerprints (8 bytes, 16 hex chars)
- **E2E Encryption:** X25519 ECDH + HKDF + ChaCha20-Poly1305 (nonce(12) || ciphertext || tag(16))
- **Messaging:** Fragmented SEND_MSG (4B ciphertext/fragment for proquint), CHECK/FETCH/ACK polling

## Build

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

## Architecture

```
app/src/main/java/com/freeflow/app/
  protocol/     Frame, Commands, Proquint, AAAA decoder
  crypto/       X25519 keys, session crypto (HKDF, HMAC tokens), E2E encryption
  client/       FreeFlowConnection (full protocol implementation)
  identity/     Identity generation, fingerprints, contacts
  data/         Repository, models
  ui/           Jetpack Compose screens with Material 3
```

## Tech Stack

- Kotlin + Jetpack Compose + Material 3
- Bouncy Castle (X25519, ChaCha20-Poly1305, HKDF, HMAC-SHA256)
- Coroutines for async DNS/HTTP operations
- Navigation Compose with bottom tabs

## Security

- X25519 key pairs generated from CSPRNG with proper clamping
- Forward secrecy via ephemeral HELLO handshake keys
- Rotating HMAC-SHA256 session tokens
- ChaCha20-Poly1305 AEAD for all E2E messages
- Proquint encoding evades DNS entropy detectors on censored networks
