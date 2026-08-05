package com.example.core.scheduling

data class Job(
    val id: String,
    val triggerType: String,
    val conditions: Map<String, String>,
    val actionCommand: String,
    val retryPolicy: String,
    val expirationTime: Long
)
