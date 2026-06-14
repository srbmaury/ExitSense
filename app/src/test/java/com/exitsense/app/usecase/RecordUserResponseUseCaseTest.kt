package com.exitsense.app.usecase

import com.exitsense.app.domain.model.ExitEvent
import com.exitsense.app.domain.model.ExitSignalType
import com.exitsense.app.domain.model.UserResponse
import com.exitsense.app.domain.repository.ExitEventRepository
import com.exitsense.app.domain.repository.LearningRepository
import com.exitsense.app.domain.usecase.RecordUserResponseUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class RecordUserResponseUseCaseTest {

    private lateinit var learningRepository: LearningRepository
    private lateinit var exitEventRepository: ExitEventRepository
    private lateinit var useCase: RecordUserResponseUseCase

    private val testEvent = ExitEvent(
        id = 1L,
        timestamp = 1_000L,
        confidenceScore = 80f,
        triggeredSignals = listOf(ExitSignalType.WIFI_DISCONNECTED),
        profileId = 5L,
        userResponded = false
    )

    @Before
    fun setUp() {
        learningRepository = mockk(relaxed = true)
        exitEventRepository = mockk()
    }

    @Test
    fun `normal flow records responses, marks event responded, and runs analysis`() = runTest {
        useCase = RecordUserResponseUseCase(learningRepository, exitEventRepository)
        coEvery { exitEventRepository.getExitEventById(1L) } returns testEvent
        coEvery { exitEventRepository.updateExitEvent(any()) } returns Unit

        val responses = listOf(
            UserResponse(exitEventId = 1L, itemId = 10L, profileId = 5L, wasConfirmed = true),
            UserResponse(exitEventId = 1L, itemId = 11L, profileId = 5L, wasConfirmed = false)
        )

        useCase(responses, exitEventId = 1L)

        coVerify { learningRepository.recordUserResponse(responses[0]) }
        coVerify { learningRepository.recordUserResponse(responses[1]) }
        coVerify { exitEventRepository.updateExitEvent(testEvent.copy(userResponded = true)) }
        coVerify { learningRepository.analyzeAndUpdatePriorities(5L) }
    }

    @Test
    fun `missing exit event short-circuits without updating or running analysis`() = runTest {
        useCase = RecordUserResponseUseCase(learningRepository, exitEventRepository)
        coEvery { exitEventRepository.getExitEventById(99L) } returns null

        val responses = listOf(
            UserResponse(exitEventId = 99L, itemId = 10L, profileId = 5L, wasConfirmed = true)
        )

        useCase(responses, exitEventId = 99L)

        coVerify(exactly = 0) { exitEventRepository.updateExitEvent(any()) }
        coVerify(exactly = 0) { learningRepository.analyzeAndUpdatePriorities(any()) }
    }

    @Test
    fun `empty response list still marks event responded but skips analysis`() = runTest {
        useCase = RecordUserResponseUseCase(learningRepository, exitEventRepository)
        coEvery { exitEventRepository.getExitEventById(1L) } returns testEvent
        coEvery { exitEventRepository.updateExitEvent(any()) } returns Unit

        useCase(emptyList(), exitEventId = 1L)

        coVerify(exactly = 0) { learningRepository.recordUserResponse(any()) }
        coVerify { exitEventRepository.updateExitEvent(testEvent.copy(userResponded = true)) }
        coVerify(exactly = 0) { learningRepository.analyzeAndUpdatePriorities(any()) }
    }
}
