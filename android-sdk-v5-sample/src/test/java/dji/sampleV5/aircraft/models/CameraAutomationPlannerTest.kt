package dji.sampleV5.aircraft.models

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraAutomationPlannerTest {
    @Test
    fun createsBoundedCapturePlan() {
        val plan = CameraAutomationPlanner.createCapturePlan(5, 2)

        assertEquals(5, plan.photoCount)
        assertEquals(2, plan.intervalSeconds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTooShortCaptureInterval() {
        CameraAutomationPlanner.createCapturePlan(5, 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnboundedRecordingDuration() {
        CameraAutomationPlanner.createRecordingPlan(901)
    }
}
