package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.jar.JarLocationResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal pipeline result retaining jar
 * provenance for each change point.
 */
final class ChangePointAnalysis {

    /** Change points. */
    private final List<ChangePoint> points;

    /** Change point jar provenance. */
    private final Map<ChangePoint,
            JarLocationResult> locations;

    ChangePointAnalysis(
            final List<ChangePoint> pointList,
            final Map<ChangePoint,
                    JarLocationResult> jarLocations) {
        this.points = Collections
                .unmodifiableList(
                        new ArrayList<>(pointList));
        this.locations = Collections
                .unmodifiableMap(
                        new LinkedHashMap<>(
                                jarLocations));
    }

    List<ChangePoint> getPoints() {
        return points;
    }

    JarLocationResult locationOf(
            final ChangePoint point) {
        return locations.get(point);
    }
}
