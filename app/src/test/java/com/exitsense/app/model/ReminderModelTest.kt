package com.exitsense.app.model

import com.exitsense.app.domain.model.ReminderItem
import com.exitsense.app.domain.model.ReminderProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderModelTest {

    @Test
    fun `effectivePriority is base priority times learnedPriority`() {
        val item = ReminderItem(profileId = 1, name = "Laptop", priority = 3, learnedPriority = 1.5f)
        assertEquals(4.5f, item.effectivePriority, 0.001f)
    }

    @Test
    fun `notifiableItems excludes disabled items`() {
        val items = listOf(
            ReminderItem(id = 1, profileId = 1, name = "Keys", isEnabled = true),
            ReminderItem(id = 2, profileId = 1, name = "Badge", isEnabled = false),
            ReminderItem(id = 3, profileId = 1, name = "Laptop", isEnabled = true)
        )
        val profile = ReminderProfile(id = 1, name = "Office", items = items)

        val result = profile.notifiableItems()

        assertEquals(2, result.size)
        assertTrue(result.none { it.name == "Badge" })
    }

    @Test
    fun `notifiableItems sorts by effective priority descending`() {
        val items = listOf(
            ReminderItem(id = 1, profileId = 1, name = "Charger", priority = 2, learnedPriority = 1.0f),   // 2.0
            ReminderItem(id = 2, profileId = 1, name = "Laptop", priority = 3, learnedPriority = 2.0f),    // 6.0
            ReminderItem(id = 3, profileId = 1, name = "Notebook", priority = 3, learnedPriority = 0.5f)   // 1.5
        )
        val profile = ReminderProfile(id = 1, name = "Office", items = items)

        val result = profile.notifiableItems()

        assertEquals(listOf("Laptop", "Charger", "Notebook"), result.map { it.name })
    }
}
