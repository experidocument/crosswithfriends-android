package com.crosswithfriends.app

data class HelpCard(
    val emoji: String,
    val title: String,
    val badge: String,
    val description: String,
    val actionText: String? = null,
    val actionUrl: String? = null
)
