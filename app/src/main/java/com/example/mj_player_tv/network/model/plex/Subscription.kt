package com.example.mj_player_tv.network.model.plex

data class Subscription(
    val active: Boolean,
    val features: List<String>,
    val paymentService: Any,
    val plan: Any,
    val status: String,
    val subscribedAt: Any
)