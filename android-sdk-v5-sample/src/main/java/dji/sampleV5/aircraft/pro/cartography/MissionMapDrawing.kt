package dji.sampleV5.aircraft.pro.cartography

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import dji.v5.ux.mapkit.core.maps.DJIMap
import dji.v5.ux.mapkit.core.models.DJIBitmapDescriptorFactory
import dji.v5.ux.mapkit.core.models.DJILatLng
import dji.v5.ux.mapkit.core.models.annotations.DJIMarkerOptions
import dji.v5.ux.mapkit.core.models.annotations.DJIPolylineOptions

/**
 * The shapes a review map shows for a planned block: the footprint, one line
 * per flight line, and one marker per control point.
 *
 * Kept apart from the activity so what gets drawn, in what order, and in which
 * colour is decided in one place.
 */
data class MissionMapDrawing(
    val footprint: List<DJILatLng>,
    val flightLines: List<List<DJILatLng>>,
    val controlPoints: List<DJILatLng>,
    val checkPoints: List<DJILatLng>,
    val start: DJILatLng?
) {
    fun allPoints(): List<DJILatLng> =
        footprint + controlPoints + checkPoints + listOfNotNull(start) + flightLines.flatten()

    val isEmpty: Boolean
        get() = allPoints().isEmpty()

    companion object {
        const val FOOTPRINT_COLOR = 0xAA2F6FED.toInt()
        const val FLIGHT_LINE_COLOR = 0xFF00C853.toInt()
        const val CONTROL_COLOR = 0xFFFF3B30.toInt()
        const val CHECK_COLOR = 0xFFFF9800.toInt()
        const val START_COLOR = 0xFFFFFFFF.toInt()
        const val FOOTPRINT_WIDTH = 3f
        const val FLIGHT_LINE_WIDTH = 4f
    }
}

/**
 * Paints a planned block on a [DJIMap].
 *
 * This is a review aid. It never sends a command to the aircraft, and the
 * activity that uses it says so on screen.
 */
object MissionMapRenderer {

    fun build(
        summary: CartographicSummary,
        controlPoints: List<GroundControlPoint>,
        startLatitude: Double? = null,
        startLongitude: Double? = null
    ): MissionMapDrawing = MissionMapDrawing(
        footprint = summary.footprintPolygon.map { (latitude, longitude) -> DJILatLng(latitude, longitude) },
        flightLines = summary.flightLines.map { line ->
            line.map { (latitude, longitude) -> DJILatLng(latitude, longitude) }
        },
        controlPoints = controlPoints.filter { !it.isCheckPoint }.map { DJILatLng(it.latitude, it.longitude) },
        checkPoints = controlPoints.filter { it.isCheckPoint }.map { DJILatLng(it.latitude, it.longitude) },
        start = if (startLatitude != null && startLongitude != null) DJILatLng(startLatitude, startLongitude) else null
    )

    fun draw(map: DJIMap, drawing: MissionMapDrawing) {
        if (drawing.footprint.size >= 3) {
            map.addPolyline(
                DJIPolylineOptions()
                    .width(MissionMapDrawing.FOOTPRINT_WIDTH)
                    .color(MissionMapDrawing.FOOTPRINT_COLOR)
                    .geodesic(true)
                    .addAll(drawing.footprint + drawing.footprint.first())
            )
        }
        drawing.flightLines.filter { it.size >= 2 }.forEach { line ->
            map.addPolyline(
                DJIPolylineOptions()
                    .width(MissionMapDrawing.FLIGHT_LINE_WIDTH)
                    .color(MissionMapDrawing.FLIGHT_LINE_COLOR)
                    .geodesic(true)
                    .addAll(line)
            )
        }
        drawing.controlPoints.forEach { map.addMarker(marker(it, MissionMapDrawing.CONTROL_COLOR)) }
        drawing.checkPoints.forEach { map.addMarker(marker(it, MissionMapDrawing.CHECK_COLOR)) }
        drawing.start?.let { map.addMarker(marker(it, MissionMapDrawing.START_COLOR)) }
    }

    private fun marker(position: DJILatLng, color: Int): DJIMarkerOptions {
        val options = DJIMarkerOptions().position(position)
        runCatching {
            options.icon(DJIBitmapDescriptorFactory.fromBitmap(colouredDot(color)))
        }
        return options
    }

    /** A filled circle with a dark rim, so it reads on satellite and on street. */
    private fun colouredDot(color: Int): Bitmap {
        val size = 36
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0x00000000)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0xFF101820.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 3f, fill)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 3f, rim)
        return bitmap
    }
}
