package com.halo.app.everyday.collectors

/** Public publisher feeds. External sources are opt-in. */
object StarterSourceCatalogue {
    val riga = WeatherPlace("Rīga", 56.95, 24.11, "Europe/Riga")
    fun local(): List<CollectorSource> = listOf(
        CollectorKind.LOCAL_ORGANIZER to "Organizer", CollectorKind.LOCAL_WORKSPACE to "Notes and plans",
        CollectorKind.LOCAL_ACTIVITY to "Halo Activity", CollectorKind.LOCAL_NOTIFICATIONS to "Halo notifications",
        CollectorKind.LOCAL_DEVICE to "Device readiness",
    ).map { (kind, title) -> CollectorSource("local:${kind.name.lowercase(java.util.Locale.ROOT)}", title, "Halo", kind,
        SourceCategory.PERSONAL, "en", selected = true, refreshMinutes = 15, childApproved = true) }

    fun external(): List<CollectorSource> = firstEdition() + expandedEdition()

    fun firstEdition(): List<CollectorSource> = listOf(
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
    fun expandedEdition(): List<CollectorSource> = listOf(
        feed("ecb-press", "European Central Bank · Press", "ECB", SourceCategory.OFFICIAL, "en",
            "https://www.ecb.europa.eu/rss/press.html", "https://www.ecb.europa.eu/home/html/rss.en.html"),
        feed("govuk-news", "UK government · News and notices", "GOV.UK", SourceCategory.OFFICIAL, "en",
            "https://www.gov.uk/search/news-and-communications.atom", "https://www.gov.uk/search/news-and-communications"),
        feed("who-news", "World Health Organization · News", "WHO", SourceCategory.OFFICIAL, "en",
            "https://www.who.int/rss-feeds/news-english.xml", "https://www.who.int/news"),
        feed("usgs-earthquakes", "USGS · Recent earthquakes", "USGS", SourceCategory.SCIENCE, "en",
            "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_day.atom",
            "https://earthquake.usgs.gov/earthquakes/feed/v1.0/atom.php"),
        feed("arxiv-ai", "arXiv · Artificial intelligence preprints", "arXiv", SourceCategory.TECHNOLOGY, "en",
            "https://rss.arxiv.org/rss/cs.AI", "https://arxiv.org/list/cs.AI/recent", refreshMinutes = 360),
        feed("arxiv-astro", "arXiv · Astrophysics preprints", "arXiv", SourceCategory.SCIENCE, "en",
            "https://rss.arxiv.org/rss/astro-ph", "https://arxiv.org/list/astro-ph/recent", refreshMinutes = 360),
        feed("nasa-apod", "NASA · Astronomy Picture of the Day", "NASA", SourceCategory.SCIENCE, "en",
            "https://science.nasa.gov/feed/apod-basic/", "https://science.nasa.gov/astronomy/photo-of-the-day/",
            refreshMinutes = 360),
    )
    fun weather(place: WeatherPlace, id: String = "weather:${java.util.UUID.randomUUID()}", selected: Boolean = true) = CollectorSource(
        id, place.name, "Open-Meteo", CollectorKind.WEATHER, SourceCategory.WEATHER, "en", OpenMeteoParser.endpoint(place),
        "https://open-meteo.com/", selected = selected, refreshMinutes = 60, place = place, childApproved = true)

    fun visible(language: String?, category: SourceCategory?): List<CollectorSource> = external().filter {
        (language == null || it.language == language) && (category == null || it.category == category)
    }
    private fun feed(id: String, title: String, publisher: String, category: SourceCategory, language: String,
        endpoint: String, attribution: String, refreshMinutes: Int = 60) =
        CollectorSource("starter:$id", title, publisher, CollectorKind.FEED, category, language, endpoint, attribution,
            refreshMinutes = refreshMinutes)
}
