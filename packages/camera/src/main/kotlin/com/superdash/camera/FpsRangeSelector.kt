package com.superdash.camera

/** Picks the Camera2 AE target FPS range for a wanted [maxFps] cap. Ranges
 *  are (lower, upper) pairs. Prefers the range with the highest upper bound
 *  that does not exceed [maxFps], tie-breaking on the lowest lower bound so
 *  AE may drop further in dim light. When no range fits under the cap, falls
 *  back to the smallest available upper bound (same tie-break). Returns null
 *  when [available] is empty. */
internal fun selectAeFpsRange(
    available: List<Pair<Int, Int>>,
    maxFps: Int,
): Pair<Int, Int>? {
    val fitting = available.filter { (_, upper) -> upper <= maxFps }
    val pool = fitting.ifEmpty { available }
    val bestUpper =
        (if (fitting.isEmpty()) pool.minOfOrNull { it.second } else pool.maxOfOrNull { it.second })
            ?: return null
    return pool
        .filter { (_, upper) -> upper == bestUpper }
        .minByOrNull { (lower, _) -> lower }
}

/** Ranges advertised by every camera in [perCamera]. CameraX picks its own
 *  camera for a lens facing, which may differ from the id we queried, so only
 *  a range that every camera of that facing supports is safe to request.
 *  Order follows the first camera's list. */
internal fun intersectFpsRanges(perCamera: List<List<Pair<Int, Int>>>): List<Pair<Int, Int>> {
    val first = perCamera.firstOrNull() ?: return emptyList()
    val common = perCamera.map { ranges -> ranges.toSet() }.reduce { acc, set -> acc intersect set }
    return first.filter { range -> range in common }
}
