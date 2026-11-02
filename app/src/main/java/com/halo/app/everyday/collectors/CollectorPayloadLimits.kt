package com.halo.app.everyday.collectors

/** Publication and restore share these limits; an accepted snapshot must remain readable after restart. */
object CollectorPayloadLimits {
    fun validateKind(kind: CollectorKind,payload: CollectorPayload) {
        require(when(kind) {
            CollectorKind.FEED -> payload is CollectorPayload.Articles
            CollectorKind.WEATHER -> payload is CollectorPayload.Weather
            CollectorKind.ALERTS -> payload is CollectorPayload.Warnings
            CollectorKind.PUBLIC_CALENDAR -> payload is CollectorPayload.Calendar
            else -> payload is CollectorPayload.Local
        }) {"Snapshot does not match its source type"}
        validate(payload)
    }
    fun validate(payload: CollectorPayload) {
        when (payload) {
            is CollectorPayload.Articles -> {
                require(payload.items.size <= 100)
                payload.items.forEach { require(it.id.length <= 2000 && it.title.length <= 1000 && it.text.length <= 100_000 &&
                    it.publisher.length <= 200 && it.categories.size <= 20 && it.categories.all { category -> category.length <= 200 }) }
            }
            is CollectorPayload.Warnings -> {
                require(payload.items.size <= 100)
                payload.items.forEach { require(it.id.length <= 2000 && it.title.length <= 1000 && it.text.length <= 100_000 && it.publisher.length <= 200) }
            }
            is CollectorPayload.Local -> {
                require(payload.items.size <= 100)
                payload.items.forEach { require(it.id.length <= 2000 && it.title.length <= 1000 && it.summary.length <= 100_000 && it.route.length <= 4096) }
            }
            is CollectorPayload.Weather -> {
                require(payload.fact.days.size <= 16)
                require(payload.fact.temperatureC?.let { it.isFinite() && it in -100.0..80.0 } != false)
                require(payload.fact.weatherCode?.let { it in 0..99 } != false)
                payload.fact.days.forEach { day ->
                    require(listOfNotNull(day.maximumC, day.minimumC).all { it.isFinite() && it in -100.0..80.0 })
                    require(day.precipitationProbability?.let { it in 0..100 } != false && day.weatherCode?.let { it in 0..99 } != false)
                }
            }
            is CollectorPayload.Calendar -> {
                require(payload.preview.records.size <= 100 && payload.preview.exceptions.size <= 2000 && payload.preview.issues.size <= 2000)
                payload.preview.records.forEach { it.validate() }
                require(payload.preview.exceptions.all { exception -> payload.preview.records.any { it.id == exception.recordId } })
            }
        }
    }
}
