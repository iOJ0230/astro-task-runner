package com.github.ioj0230.astro.core.meteor

interface AstroEventService {
    fun upcomingMeteorShowers(request: MeteorAlertRequest): MeteorAlertResponse
}