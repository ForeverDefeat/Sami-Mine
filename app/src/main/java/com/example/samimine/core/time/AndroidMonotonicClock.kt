package com.example.samimine.core.time

import android.os.SystemClock
import com.example.samimine.domain.evaluation.pvt.MonotonicClock

class AndroidMonotonicClock : MonotonicClock {
    override fun nowNanos(): Long = SystemClock.elapsedRealtimeNanos()
}
