package com.halo.app.everyday.collectors

/** Endpoints validated on 2026-11-01; ordinary fetch failures remain visible and never fabricate news. */
object StarterSourceCatalogue {
    val riga = WeatherPlace("Rīga", 56.95, 24.11, "Europe/Riga")
    fun local(): List<CollectorSource> = listOf(
        CollectorKind.LOCAL_ORGANIZER to "Organizer", CollectorKind.LOCAL_WORKSPACE to "Notes and plans",
        CollectorKind.LOCAL_ACTIVITY to "Halo Activity", CollectorKind.LOCAL_NOTIFICATIONS to "Halo notifications",
        CollectorKind.LOCAL_DEVICE to "Device readiness",
    ).map { (kind, title) -> CollectorSource("local:${kind.name.lowercase(java.util.Locale.ROOT)}", title, "Halo", kind,
        SourceCategory.PERSONAL, "en", selected = true, refreshMinutes = 15, childApproved = true) }

    fun external(): List<CollectorSource> = listOf(
        feed("lsm-lv", "LSM · Latvian", "LSM", SourceCategory.LATVIA, "lv", "https://www.lsm.lv/rss/?lang=lv&catid=14", "https://www.lsm.lv/"),
        feed("lsm-ru", "LSM · Russian", "LSM", SourceCategory.LATVIA, "ru", "https://rus.lsm.lv/rss/", "https://rus.lsm.lv/"),
        feed("lsm-en-politics", "LSM · Politics", "LSM", SourceCategory.LATVIA, "en", "https://eng.lsm.lv/rss/?lang=en&catid=315", "https://eng.lsm.lv/"),
        feed("lsm-en-society", "LSM · Society", "LSM", SourceCategory.LATVIA, "en", "https://eng.lsm.lv/rss/?lang=en&catid=278", "https://eng.lsm.lv/"),
        feed("bbc-world", "BBC World", "BBC", SourceCategory.WORLD, "en", "https://feeds.bbci.co.uk/news/world/rss.xml", "https://www.bbc.com/news/world"),
        feed("nasa", "NASA", "NASA", SourceCategory.SCIENCE, "en", "https://www.nasa.gov/feed/", "https://www.nasa.gov/"),
        feed("ars", "Ars Technica", "Ars Technica", SourceCategory.TECHNOLOGY, "en", "https://feeds.arstechnica.com/arstechnica/index", "https://arstechnica.com/"),
        feed("verge", "The Verge", "The Verge", SourceCategory.TECHNOLOGY, "en", "https://www.theverge.com/rss/index.xml", "https://www.theverge.com/"),
        feed("hacker-news", "Hacker News", "Hacker News", SourceCategory.TECHNOLOGY, "en", "https://news.ycombinator.com/rss", "https://news.ycombinator.com/"),
        feed("cert-lv", "CERT.LV notices", "CERT.LV", SourceCategory.OFFICIAL, "lv", "https://cert.lv/lv/rss/incidenti/bridinajumi.xml", "https://cert.lv/"),
        CollectorSource("starter:meteoalarm-lv", "Latvia weather warnings", "Meteoalarm", CollectorKind.ALERTS, SourceCategory.WEATHER, "en",
            "https://feeds.meteoalarm.org/feeds/meteoalarm-legacy-atom-latvia", "https://www.meteoalarm.org/", refreshMinutes = 60, childApproved = true),
    )
    fun weather(place: WeatherPlace, id: String = "weather:${java.util.UUID.randomUUID()}", selected: Boolean = true) = CollectorSource(
        id, place.name, "Open-Meteo", CollectorKind.WEATHER, SourceCategory.WEATHER, "en", OpenMeteoParser.endpoint(place),
        "https://open-meteo.com/", selected = selected, refreshMinutes = 60, place = place, childApproved = true)

    fun visible(language: String?, category: SourceCategory?): List<CollectorSource> = external().filter {
        (language == null || it.language == language) && (category == null || it.category == category)
    }
    private fun feed(id: String, title: String, publisher: String, category: SourceCategory, language: String, endpoint: String, attribution: String) =
        CollectorSource("starter:$id", title, publisher, CollectorKind.FEED, category, language, endpoint, attribution)
}
