package com.superdash.screensaver.slideshow

/** Bounded LRU-style history of recently shown slideshow items with a
 *  navigable cursor. Generic: works for any [SlideshowSource]. Not
 *  thread-safe; callers must serialize access (the screensaver does so
 *  via Compose's single-thread recomposition model). */
class SlideshowHistory(
    private val capacity: Int = 20,
) {
    private val items = ArrayDeque<SlideshowItem>()
    private var cursor: Int = -1

    val current: SlideshowItem?
        get() = items.getOrNull(cursor)

    /** Append [item] at the tail, move cursor to it, and trim the head if
     *  over [capacity]. If the cursor was not at the tail (user had gone
     *  back), drops the forward branch first. */
    fun pushAndAdvance(item: SlideshowItem) {
        while (items.size > cursor + 1) items.removeLast()
        items.addLast(item)
        cursor = items.lastIndex
        while (items.size > capacity) {
            items.removeFirst()
            cursor--
        }
    }

    /** Step the cursor back one position. Returns true if it moved. */
    fun goBack(): Boolean {
        if (cursor <= 0) return false
        cursor--
        return true
    }

    /** Step the cursor forward one position within the existing buffer.
     *  Returns true if it moved; false if at the tail (caller should
     *  fetch a new item and call [pushAndAdvance]). */
    fun goForward(): Boolean {
        if (cursor < 0 || cursor >= items.lastIndex) return false
        cursor++
        return true
    }
}
