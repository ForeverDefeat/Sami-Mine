package dev.studios.samimine.core.time

import android.os.SystemClock
import dev.studios.samimine.domain.evaluation.pvt.MonotonicClock

/** Reloj PVT en nanosegundos desde el arranque, incluido sueño; no es epoch ni uptime táctil. */
class AndroidMonotonicClock : MonotonicClock {
    override fun nowNanos(): Long = SystemClock.elapsedRealtimeNanos()
}
