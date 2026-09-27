package dji.sampleV5.aircraft.pro.mission

import android.content.Context
import com.dji.wpmzsdk.common.data.Template
import com.dji.wpmzsdk.common.utils.kml.model.WaypointActionType
import com.dji.wpmzsdk.manager.WPMZManager
import dji.sampleV5.aircraft.models.MissionGlobalModel
import dji.sampleV5.aircraft.utils.KMZTestUtil
import dji.sampleV5.aircraft.utils.wpml.WaypointInfoModel
import dji.sdk.wpmz.value.mission.WaylineExitOnRCLostAction
import dji.sdk.wpmz.value.mission.WaylineFinishedAction
import dji.sdk.wpmz.value.mission.WaylineLocationCoordinate2D
import dji.sdk.wpmz.value.mission.WaylineLocationCoordinate3D
import dji.sdk.wpmz.value.mission.WaylineWaypoint
import dji.sdk.wpmz.value.mission.WaylineWaypointGimbalHeadingMode
import dji.sdk.wpmz.value.mission.WaylineWaypointGimbalHeadingParam
import dji.sdk.wpmz.value.mission.WaylineWaypointYawMode
import dji.sdk.wpmz.value.mission.WaylineWaypointYawParam
import dji.sdk.wpmz.value.mission.WaylineWaypointYawPathMode
import java.io.File
import kotlin.math.min

/** Converts the validated, model-independent plan into a DJI WPML/KMZ file. */
object WpmlMissionExporter {
    @Synchronized
    fun export(context: Context, plan: MissionPlan): File {
        WPMZManager.getInstance().init(context.applicationContext)
        val outputDirectory = File(context.cacheDir, "generated-missions").apply { mkdirs() }
        val output = File(outputDirectory, "${safeName(plan.id)}.kmz")
        if (output.exists()) output.delete()

        val request = plan.request
        val validation = MissionValidator.validate(request, plan)
        check(validation.isValid) { "No se puede exportar una misión no válida: ${validation.errors.joinToString("; ")}" }
        val global = MissionGlobalModel(
            globalSpeed = request.speedMps,
            finishAction = when (request.finishAction) {
                FinishAction.RETURN_HOME -> WaylineFinishedAction.GO_HOME
                FinishAction.HOVER -> WaylineFinishedAction.NO_ACTION
                FinishAction.LAND -> WaylineFinishedAction.AUTO_LAND
            },
            lostAction = WaylineExitOnRCLostAction.GO_BACK
        )
        val config = KMZTestUtil.createMissionConfig(global)
        config.securityTakeOffHeight = min(20.0, request.altitudeMeters)
        config.globalTransitionalSpeed = request.speedMps.coerceAtLeast(2.0)
        config.isSecurityTakeOffHeightSet = true

        val template: Template = KMZTestUtil.createTemplate(plan.waypoints.map { point ->
            val waypoint = WaylineWaypoint().apply {
                waypointIndex = point.index
                location = WaylineLocationCoordinate2D(point.latitude, point.longitude)
                height = point.heightMeters
                ellipsoidHeight = point.heightMeters
                speed = point.speedMps
                useGlobalAutoFlightSpeed = true
                useGlobalFlightHeight = false
                useGlobalTurnParam = true
                useStraightLine = true
                gimbalPitchAngle = point.pitchDegrees
                yawParam = WaylineWaypointYawParam().apply {
                    yawMode = WaylineWaypointYawMode.FOLLOW_WAYLINE
                    enableYawAngle = false
                    yawPathMode = WaylineWaypointYawPathMode.FOLLOW_BAD_ARC
                    poiLocation = WaylineLocationCoordinate3D(point.latitude, point.longitude, 0.0)
                }
                isWaylineWaypointYawParamSet = true
                useGlobalYawParam = false
                gimbalHeadingParam = WaylineWaypointGimbalHeadingParam().apply {
                    headingMode = WaylineWaypointGimbalHeadingMode.FOLLOW_WAYLINE
                    pitchAngle = point.pitchDegrees
                }
                isWaylineWaypointGimbalHeadingParamSet = true
                useGlobalGimbalHeadingParam = false
            }
            WaypointInfoModel().apply {
                waylineWaypoint = waypoint
                actionInfos = if (point.takePhoto) {
                    listOf(KMZTestUtil.createActionInfo(WaypointActionType.START_TAKE_PHOTO, 0))
                } else {
                    emptyList()
                }
            }
        })
        template.autoFlightSpeed = request.speedMps
        template.transitionalSpeed = request.speedMps.coerceAtLeast(2.0)
        template.waypointInfo.globalFlightHeight = request.altitudeMeters
        template.waypointInfo.isGlobalFlightHeightSet = true
        template.waypointInfo.isTemplateGlobalYawParamSet = true

        WPMZManager.getInstance().generateKMZFile(
            output.absolutePath,
            KMZTestUtil.createWaylineMission(),
            config,
            template
        )
        check(output.exists() && output.length() > 0L) { "El generador WPML de DJI no ha creado ningún archivo KMZ" }
        return output
    }

    private fun safeName(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
