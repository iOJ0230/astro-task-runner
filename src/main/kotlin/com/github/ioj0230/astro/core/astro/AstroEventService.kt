package com.github.ioj0230.astro.core.astro

import com.github.ioj0230.astro.core.meteors.MeteorAlertRequest
import com.github.ioj0230.astro.core.meteors.MeteorAlertResponse

interface AstroEventService {
    fun upcomingMeteorShowers(request: MeteorAlertRequest): MeteorAlertResponse
}