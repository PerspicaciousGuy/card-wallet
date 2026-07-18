// This file holds ALL routes; card detail/form join in Phase 3, when the
// filename will match plurality again.
@file:Suppress("MatchingDeclarationName", "ktlint:standard:filename")

package com.cardwallet.navigation

import kotlinx.serialization.Serializable

// Type-safe routes (compose-rules.md §7). Routes carry record IDs only —
// never card payloads (IMPLEMENTATION_PLAN.md §3 rule 1). The lock screen is
// NOT a route: MainActivity swaps the whole composition on session state, so
// locked content is unreachable by navigation (F2.6).

@Serializable
data object HomeRoute
