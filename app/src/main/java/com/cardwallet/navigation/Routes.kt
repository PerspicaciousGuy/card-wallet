package com.cardwallet.navigation

import kotlinx.serialization.Serializable

// Type-safe routes (compose-rules.md §7). Routes carry record IDs only —
// never card payloads (IMPLEMENTATION_PLAN.md §3 rule 1).

@Serializable
data object LockRoute

@Serializable
data object HomeRoute
