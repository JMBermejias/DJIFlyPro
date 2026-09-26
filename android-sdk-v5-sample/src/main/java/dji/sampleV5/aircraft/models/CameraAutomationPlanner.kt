package dji.sampleV5.aircraft.models

data class CapturePlan(
    val photoCount: Int,
    val intervalSeconds: Int
)

data class RecordingPlan(
    val durationSeconds: Int
)

object CameraAutomationPlanner {
    const val MAX_PHOTO_COUNT = 100
    const val MIN_INTERVAL_SECONDS = 2
    const val MAX_INTERVAL_SECONDS = 300
    const val MAX_RECORDING_SECONDS = 900

    fun createCapturePlan(photoCount: Int, intervalSeconds: Int): CapturePlan {
        require(photoCount in 1..MAX_PHOTO_COUNT)
        require(intervalSeconds in MIN_INTERVAL_SECONDS..MAX_INTERVAL_SECONDS)
        return CapturePlan(photoCount, intervalSeconds)
    }

    fun createRecordingPlan(durationSeconds: Int): RecordingPlan {
        require(durationSeconds in 1..MAX_RECORDING_SECONDS)
        return RecordingPlan(durationSeconds)
    }
}
