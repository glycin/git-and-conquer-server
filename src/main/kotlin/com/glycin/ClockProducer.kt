package com.glycin

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton
import java.time.Clock

@ApplicationScoped
class ClockProducer {
    @Produces
    @Singleton
    fun clock(): Clock = Clock.systemUTC()
}
