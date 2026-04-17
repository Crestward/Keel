// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.agent

import com.keel.agent.tool.ToolRegistry
import com.keel.database.repository.AgentMemoryRepository
import com.keel.model.AgentTrigger
import com.keel.model.ToolResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.LinkedList

class ReActLoopTest {

    private lateinit var contextBuilder: ContextBuilder
    private lateinit var toolRegistry: ToolRegistry
    private lateinit var memoryRepository: AgentMemoryRepository

    @Before
    fun setup() {
        contextBuilder = mockk()
        coEvery { contextBuilder.buildSystemInstruction(any()) } returns "You are Keel."
        every { contextBuilder.buildTriggerMessage(any()) } returns "TASK: periodic review"
        every { contextBuilder.buildToolResultMessage(any(), any()) } answers {
            "[TOOL_RESULT: ${firstArg<String>()}]\n${secondArg<String>()}"
        }

        toolRegistry = mockk(relaxed = true)
        coEvery { toolRegistry.execute(any(), any()) } returns ToolResult.Success(
            toolName = "get_spending_summary",
            resultJson = """{"income_naira":100000,"expenses_naira":80000,"net_naira":20000,"top_merchants":[],"avg_daily_naira":2666}"""
        )

        memoryRepository = mockk(relaxed = true)
        coEvery { memoryRepository.addRecallMemory(any(), any(), any()) } returns Unit
    }

    // ─── Happy path ───────────────────────────────────────────────────────────

    @Test
    fun `2-iteration run - tool call then done`() = runTest {
        val fake = FakeOnDeviceBackend(
            LinkedList(
                listOf(
                    """{"thought":"let me check spending","tool":"get_spending_summary","args":{"period":"month"}}""",
                    """{"thought":"spending looks normal, nothing to report","done":true}""",
                )
            )
        )
        val loop = ReActLoop(fake, contextBuilder, toolRegistry, memoryRepository)

        val run = loop.run(AgentTrigger.PeriodicReview)

        assertEquals(2, run.iterationCount)
        assertEquals("DONE", run.terminationReason)
        coVerify(exactly = 1) { toolRegistry.execute("get_spending_summary", any()) }
        coVerify(exactly = 1) { memoryRepository.addRecallMemory(any(), any(), any()) }
    }

    @Test
    fun `create_insight tool call captured in insightsCreated`() = runTest {
        val insightId = 42L
        coEvery { toolRegistry.execute("create_insight", any()) } returns ToolResult.Success(
            toolName = "create_insight",
            resultJson = """{"insight_id":$insightId}"""
        )

        val fake = FakeOnDeviceBackend(
            LinkedList(
                listOf(
                    """{"thought":"spending spike detected","tool":"create_insight","args":{"title":"Spending spike","body":"Up 40% this week","severity":"WARNING"}}""",
                    """{"thought":"insight created, done","done":true}""",
                )
            )
        )
        val loop = ReActLoop(fake, contextBuilder, toolRegistry, memoryRepository)

        val run = loop.run(AgentTrigger.PeriodicReview)

        assertEquals("DONE", run.terminationReason)
        assertEquals(listOf(insightId), run.insightsCreated)
    }

    // ─── Termination reasons ──────────────────────────────────────────────────

    @Test
    fun `MAX_ITERATIONS reached - terminationReason is MAX_ITERATIONS`() = runTest {
        // Queue has exactly MAX_ITERATIONS tool calls and no done — loop exhausts itself
        val toolCallResponse =
            """{"thought":"still thinking","tool":"get_spending_summary","args":{"period":"month"}}"""
        val responses = LinkedList(List(ReActLoop.MAX_ITERATIONS) { toolCallResponse })
        val fake = FakeOnDeviceBackend(responses)
        val loop = ReActLoop(fake, contextBuilder, toolRegistry, memoryRepository)

        val run = loop.run(AgentTrigger.PeriodicReview)

        assertEquals("MAX_ITERATIONS", run.terminationReason)
        assertEquals(ReActLoop.MAX_ITERATIONS, run.iterationCount)
    }

    @Test
    fun `PARSE_ERROR on second response - terminationReason is PARSE_ERROR`() = runTest {
        val fake = FakeOnDeviceBackend(
            LinkedList(
                listOf(
                    // First response: valid tool call
                    """{"thought":"checking","tool":"get_spending_summary","args":{"period":"month"}}""",
                    // Second response: garbage — not valid JSON
                    "I am a confused language model. Let me think...",
                )
            )
        )
        val loop = ReActLoop(fake, contextBuilder, toolRegistry, memoryRepository)

        val run = loop.run(AgentTrigger.PeriodicReview)

        assertEquals("PARSE_ERROR", run.terminationReason)
    }

    @Test
    fun `PARSE_ERROR on first response retries once with JSON reminder then succeeds`() = runTest {
        val fake = FakeOnDeviceBackend(
            LinkedList(
                listOf(
                    // First response: garbage — triggers JSON retry
                    "Sure! I can help with that. Let me think...",
                    // Second response (after retry): valid done — loop exits cleanly
                    """{"thought":"after retry","done":true}""",
                )
            )
        )
        val loop = ReActLoop(fake, contextBuilder, toolRegistry, memoryRepository)

        val run = loop.run(AgentTrigger.PeriodicReview)

        // The retry doesn't increment iteration, so iteration ends at 1 (the done response)
        assertEquals("DONE", run.terminationReason)
    }

    // ─── Trigger variants ─────────────────────────────────────────────────────

    @Test
    fun `NewTransactions trigger - run completes and records triggeredBy`() = runTest {
        val fake = FakeOnDeviceBackend(LinkedList(listOf("""{"thought":"ok","done":true}""")))
        val loop = ReActLoop(fake, contextBuilder, toolRegistry, memoryRepository)

        val run = loop.run(AgentTrigger.NewTransactions(count = 3))

        assertTrue(run.triggeredBy is AgentTrigger.NewTransactions)
        assertEquals(3, (run.triggeredBy as AgentTrigger.NewTransactions).count)
        assertEquals("DONE", run.terminationReason)
    }

    @Test
    fun `UserQuery trigger - run completes and records triggeredBy`() = runTest {
        val fake = FakeOnDeviceBackend(LinkedList(listOf("""{"thought":"answered","done":true}""")))
        val loop = ReActLoop(fake, contextBuilder, toolRegistry, memoryRepository)

        val run = loop.run(AgentTrigger.UserQuery(text = "How much did I spend on food?"))

        assertTrue(run.triggeredBy is AgentTrigger.UserQuery)
        assertEquals("DONE", run.terminationReason)
    }

    // ─── AgentRun fields ──────────────────────────────────────────────────────

    @Test
    fun `AgentRun has positive durationMs and non-zero id`() = runTest {
        val fake = FakeOnDeviceBackend(LinkedList(listOf("""{"thought":"ok","done":true}""")))
        val loop = ReActLoop(fake, contextBuilder, toolRegistry, memoryRepository)

        val run = loop.run(AgentTrigger.PeriodicReview)

        assertTrue("durationMs should be >= 0", run.durationMs >= 0)
        assertTrue("id should be non-zero", run.id > 0)
    }

    @Test
    fun `ToolResult Error does not crash the loop`() = runTest {
        coEvery { toolRegistry.execute(any(), any()) } returns ToolResult.Error(
            toolName = "get_spending_summary",
            message = "Database unavailable"
        )
        val fake = FakeOnDeviceBackend(
            LinkedList(
                listOf(
                    """{"thought":"checking spending","tool":"get_spending_summary","args":{"period":"month"}}""",
                    """{"thought":"got an error but ok","done":true}""",
                )
            )
        )
        val loop = ReActLoop(fake, contextBuilder, toolRegistry, memoryRepository)

        val run = loop.run(AgentTrigger.PeriodicReview)

        assertEquals("DONE", run.terminationReason)
    }
}
