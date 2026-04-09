package com.example.androidapp.framework.adapter.output.file

import com.example.androidapp.domain.model.HrvSession
import com.example.androidapp.domain.model.HrvSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HrvSessionFileAdapterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var adapter: HrvSessionFileAdapter

    @Before
    fun setUp() {
        adapter = HrvSessionFileAdapter(tempFolder.root)
    }

    @Test
    fun createSessionWritesJsonlFileWithHeader() = runTest {
        adapter.createSession(HrvSession(id = "s1", startTime = 1000L))
        val files = tempFolder.root.resolve("hrv_sessions").listFiles()
        assertNotNull(files)
        assertEquals(1, files!!.size)
        assertTrue(files[0].readText().contains("\"id\":\"s1\""))
    }

    @Test
    fun appendSnapshotAddsLineToSessionFile() = runTest {
        adapter.createSession(HrvSession(id = "s1", startTime = 1000L))
        adapter.appendSnapshot("s1", HrvSnapshot(2000L, 42.3, 312, 300_000L))
        val lines = tempFolder.root.resolve("hrv_sessions").listFiles()!!.first()
            .readLines().filter { it.isNotBlank() }
        assertEquals(2, lines.size)
        assertTrue(lines[1].contains("\"rmssdMs\":42.3"))
    }

    @Test
    fun appendSnapshotSupportsMultipleSnapshots() = runTest {
        adapter.createSession(HrvSession(id = "s1", startTime = 1000L))
        repeat(5) { i ->
            adapter.appendSnapshot("s1", HrvSnapshot(2000L + i * 60_000L, 40.0 + i, 300, 300_000L))
        }
        val lines = tempFolder.root.resolve("hrv_sessions").listFiles()!!.first()
            .readLines().filter { it.isNotBlank() }
        assertEquals(6, lines.size)
    }

    @Test
    fun loadAllReturnsEmptyListWhenNoFiles() = runTest {
        assertEquals(emptyList<HrvSession>(), adapter.loadAll())
    }

    @Test
    fun findByIdReturnsSessionWhenFileExists() = runTest {
        adapter.createSession(HrvSession(id = "s1", startTime = 1000L))
        adapter.appendSnapshot("s1", HrvSnapshot(2000L, 42.3, 312, 300_000L))
        val session = adapter.findById("s1")
        assertNotNull(session)
        assertEquals("s1", session!!.id)
        assertEquals(1, session.snapshots.size)
    }

    @Test
    fun findByIdReturnsNullWhenFileDoesNotExist() = runTest {
        assertNull(adapter.findById("nonexistent"))
    }

    @Test
    fun loadAllRoundTripsSessionWithSnapshots() = runTest {
        adapter.createSession(HrvSession(id = "s1", startTime = 1000L))
        adapter.appendSnapshot("s1", HrvSnapshot(2000L, 42.3, 312, 300_000L))
        adapter.appendSnapshot("s1", HrvSnapshot(3000L, 44.1, 308, 300_000L))
        val sessions = adapter.loadAll()
        assertEquals(1, sessions.size)
        assertEquals("s1", sessions[0].id)
        assertNull(sessions[0].endTime)
        assertEquals(2, sessions[0].snapshots.size)
        assertEquals(42.3, sessions[0].snapshots[0].rmssdMs, 0.001)
    }

    @Test
    fun loadAllReturnsSessionsSortedByStartTimeDescending() = runTest {
        adapter.createSession(HrvSession(id = "older", startTime = 1000L))
        adapter.createSession(HrvSession(id = "newer", startTime = 5000L))
        val sessions = adapter.loadAll()
        assertEquals("newer", sessions[0].id)
        assertEquals("older", sessions[1].id)
    }

    @Test
    fun finaliseSessionUpdatesEndTimeAndPreservesSnapshots() = runTest {
        adapter.createSession(HrvSession(id = "s1", startTime = 1000L))
        adapter.appendSnapshot("s1", HrvSnapshot(2000L, 42.3, 312, 300_000L))
        adapter.finaliseSession(HrvSession(id = "s1", startTime = 1000L, endTime = 9000L))
        val session = adapter.loadAll().first()
        assertEquals(9000L, session.endTime)
        assertEquals(1, session.snapshots.size)
    }

    @Test
    fun fullLifecycleCreateAppendFinaliseLoad() = runTest {
        adapter.createSession(HrvSession(id = "full", startTime = 100L))
        listOf(
            HrvSnapshot(400L, 40.0, 300, 300_000L),
            HrvSnapshot(460L, 45.0, 305, 300_000L),
            HrvSnapshot(520L, 50.0, 310, 300_000L)
        ).forEach { adapter.appendSnapshot("full", it) }
        adapter.finaliseSession(HrvSession(id = "full", startTime = 100L, endTime = 600L))
        val session = adapter.loadAll().first()
        assertEquals("full", session.id)
        assertEquals(600L, session.endTime)
        assertEquals(3, session.snapshots.size)
        assertEquals(45.0, session.averageRmssd!!, 0.001)
    }
}
