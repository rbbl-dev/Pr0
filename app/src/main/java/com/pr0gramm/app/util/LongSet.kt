package com.pr0gramm.app.util

import androidx.collection.LongSparseArray

class LongSet private constructor(private val values: LongArray) {
    constructor() : this(longArrayOf())

    val size: Int get() = values.size

    operator fun plus(other: LongSet): LongSet {
        return ofValues(values + other.values)
    }

    operator fun contains(value: Long): Boolean {
        return values.binarySearch(value) >= 0
    }

    override fun hashCode(): Int {
        return values.contentHashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is LongSet && other.values.contentEquals(values)
    }

    companion object {
        fun keysOf(arr: LongSparseArray<*>): LongSet {
            return LongSet(LongArray(arr.size()) { idx -> arr.keyAt(idx) })
        }

        fun ofValues(values: LongArray): LongSet {
            return LongSet(values.sortedArray().distinct().toLongArray())
        }
    }
}