// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database.entity

import com.keel.model.Account
import com.keel.model.AgentMemory
import com.keel.model.AgentRun
import com.keel.model.Category
import com.keel.model.Insight
import com.keel.model.Merchant
import com.keel.model.RawEvent
import com.keel.model.Transaction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ─── RawEvent ─────────────────────────────────────────────────────────────────

fun RawEventEntity.toModel() = RawEvent(
    id = id,
    senderAddress = senderAddress,
    senderPackage = senderPackage,
    body = body,
    bodyHash = bodyHash,
    source = source,
    receivedAt = receivedAt,
    processed = processed,
    needsLlmParsing = needsLlmParsing,
)

fun RawEvent.toEntity() = RawEventEntity(
    id = id,
    senderAddress = senderAddress,
    senderPackage = senderPackage,
    body = body,
    bodyHash = bodyHash,
    source = source,
    receivedAt = receivedAt,
    processed = processed,
    needsLlmParsing = needsLlmParsing,
)

// ─── Transaction ──────────────────────────────────────────────────────────────

fun TransactionEntity.toModel() = Transaction(
    id = id,
    amount = amount,
    type = type,
    merchant = merchant,
    category = category,
    balance = balance,
    rawText = rawText,
    source = source,
    parsed = parsed,
    accountId = accountId,
    agentRunId = agentRunId,
    timestamp = timestamp,
)

fun Transaction.toEntity() = TransactionEntity(
    id = id,
    amount = amount,
    type = type,
    merchant = merchant,
    category = category,
    balance = balance,
    rawText = rawText,
    source = source,
    parsed = parsed,
    accountId = accountId,
    agentRunId = agentRunId,
    timestamp = timestamp,
)

// ─── Insight ──────────────────────────────────────────────────────────────────

fun InsightEntity.toModel() = Insight(
    id = id,
    title = title,
    body = body,
    severity = severity,
    agentGenerated = agentGenerated,
    agentRunId = agentRunId,
    dismissed = dismissed,
    snoozedUntil = snoozedUntil,
    createdAt = createdAt,
)

fun Insight.toEntity() = InsightEntity(
    id = id,
    title = title,
    body = body,
    severity = severity,
    agentGenerated = agentGenerated,
    agentRunId = agentRunId,
    dismissed = dismissed,
    snoozedUntil = snoozedUntil,
    createdAt = createdAt,
)

// ─── AgentMemory ──────────────────────────────────────────────────────────────

fun AgentMemoryEntity.toModel() = AgentMemory(
    id = id,
    key = key,
    content = content,
    memoryType = memoryType,
    embedding = embedding,
    confidence = confidence,
    accessCount = accessCount,
    expiresAt = expiresAt,
)

fun AgentMemory.toEntity() = AgentMemoryEntity(
    id = id,
    key = key,
    content = content,
    memoryType = memoryType,
    embedding = embedding,
    confidence = confidence,
    accessCount = accessCount,
    expiresAt = expiresAt,
)

// ─── AgentRun ─────────────────────────────────────────────────────────────────

fun AgentRunEntity.toModel() = AgentRun(
    id = id,
    triggeredBy = triggeredBy,
    iterationCount = iterationCount,
    toolCallsJson = toolCallsJson,
    insightsCreated = insightsCreated,
    durationMs = durationMs,
    terminationReason = terminationReason,
    timestamp = timestamp,
)

fun AgentRun.toEntity() = AgentRunEntity(
    id = id,
    triggeredBy = triggeredBy,
    iterationCount = iterationCount,
    toolCallsJson = toolCallsJson,
    insightsCreated = insightsCreated,
    durationMs = durationMs,
    terminationReason = terminationReason,
    timestamp = timestamp,
)

// ─── Merchant ─────────────────────────────────────────────────────────────────

private val json = Json { ignoreUnknownKeys = true }

fun MerchantEntity.toModel() = Merchant(
    id = id,
    name = name,
    variants = json.decodeFromString(variants),
    category = category,
    isSubscription = isSubscription,
    subscriptionIntervalDays = subscriptionIntervalDays,
)

fun Merchant.toEntity() = MerchantEntity(
    id = id,
    name = name,
    variants = json.encodeToString(variants),
    category = category,
    isSubscription = isSubscription,
    subscriptionIntervalDays = subscriptionIntervalDays,
)

// ─── Category ─────────────────────────────────────────────────────────────────

fun CategoryEntity.toModel() = Category(
    id = id,
    name = name,
    slug = slug,
    parentId = parentId,
    icon = icon,
)

fun Category.toEntity() = CategoryEntity(
    id = id,
    name = name,
    slug = slug,
    parentId = parentId,
    icon = icon,
)

// ─── Account ──────────────────────────────────────────────────────────────────

fun AccountEntity.toModel() = Account(
    id = id,
    bank = bank,
    maskedNumber = maskedNumber,
    balanceKobo = balanceKobo,
    nickname = nickname,
)

fun Account.toEntity() = AccountEntity(
    id = id,
    bank = bank,
    maskedNumber = maskedNumber,
    balanceKobo = balanceKobo,
    nickname = nickname,
)
