package com.cardwallet.navigation

import kotlinx.serialization.Serializable

// Type-safe routes (compose-rules.md §7). Routes carry record IDs only —
// never card payloads (IMPLEMENTATION_PLAN.md §3 rule 1). The lock screen is
// NOT a route: MainActivity swaps the whole composition on session state, so
// locked content is unreachable by navigation (F2.6).

@Serializable
data object HomeRoute

@Serializable
data class CardDetailRoute(
    val cardId: String,
)

/** cardId == null means "add a new card". */
@Serializable
data class CardFormRoute(
    val cardId: String? = null,
)

@Serializable
data object ChangePinRoute
