package com.personal.sleepalarm.domain.model

/**
 * Semantic, code-native visual attached to a challenge. It intentionally contains no Android
 * bitmap or Compose types, so generators remain deterministic and unit-testable offline.
 */
sealed interface ChallengeVisual {
    val contentDescription: String

    data class FunctionGraph(
        val xMin: Double,
        val xMax: Double,
        val yMin: Double,
        val yMax: Double,
        val series: List<GraphSeries>,
        val points: List<VisualPoint> = emptyList(),
        override val contentDescription: String
    ) : ChallengeVisual {
        init {
            require(xMin.isFinite() && xMax.isFinite() && yMin.isFinite() && yMax.isFinite())
            require(xMin < xMax && yMin < yMax)
            require(series.isNotEmpty())
            require(contentDescription.isNotBlank())
            require(series.flatMap(GraphSeries::points).all(VisualPoint::hasFiniteCoordinates))
            require(points.all(VisualPoint::hasFiniteCoordinates))
        }
    }

    data class NumberLine(
        val min: Double,
        val max: Double,
        val intervals: List<NumberLineInterval> = emptyList(),
        val points: List<NumberLinePoint> = emptyList(),
        override val contentDescription: String
    ) : ChallengeVisual {
        init {
            require(min.isFinite() && max.isFinite() && min < max)
            require(contentDescription.isNotBlank())
            require(intervals.all { interval ->
                (interval.start == null || interval.start.isFinite()) &&
                    (interval.end == null || interval.end.isFinite()) &&
                    (interval.start == null || interval.end == null || interval.start < interval.end)
            })
            require(points.all { it.value.isFinite() })
        }
    }

    data class GeometryDiagram(
        val points: List<VisualPoint>,
        val segments: List<GeometrySegment>,
        val circles: List<GeometryCircle> = emptyList(),
        val polygons: List<GeometryPolygon> = emptyList(),
        override val contentDescription: String
    ) : ChallengeVisual {
        init {
            require(points.isNotEmpty())
            require(points.all(VisualPoint::hasFiniteCoordinates))
            require(contentDescription.isNotBlank())
            require(segments.all { it.fromPointIndex in points.indices && it.toPointIndex in points.indices })
            require(circles.all { it.centerPointIndex in points.indices && it.radius.isFinite() && it.radius > 0.0 })
            require(polygons.all { polygon ->
                polygon.pointIndices.size >= 3 && polygon.pointIndices.all(points.indices::contains)
            })
        }
    }
}

data class VisualPoint(
    val x: Double,
    val y: Double,
    val label: String? = null,
    val emphasized: Boolean = false
)

private fun VisualPoint.hasFiniteCoordinates(): Boolean = x.isFinite() && y.isFinite()

data class GraphSeries(
    val points: List<VisualPoint>,
    val dashed: Boolean = false
) {
    init { require(points.size >= 2) }
}

data class NumberLineInterval(
    /** Null denotes negative infinity. */
    val start: Double?,
    /** Null denotes positive infinity. */
    val end: Double?,
    val startInclusive: Boolean = false,
    val endInclusive: Boolean = false
)

data class NumberLinePoint(
    val value: Double,
    val label: String? = null,
    val filled: Boolean = true
)

data class GeometrySegment(
    val fromPointIndex: Int,
    val toPointIndex: Int,
    val dashed: Boolean = false
)

data class GeometryCircle(
    val centerPointIndex: Int,
    val radius: Double
)

data class GeometryPolygon(
    val pointIndices: List<Int>,
    val filled: Boolean = false
)
