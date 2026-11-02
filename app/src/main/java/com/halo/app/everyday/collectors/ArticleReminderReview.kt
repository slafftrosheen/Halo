package com.halo.app.everyday.collectors

/** Immutable user review, checked again inside the organizer/source database transaction. */
data class ArticleReminderReview(val sourceId: String, val sourceVersion: Long, val contentRevision: String, val article: ArticleFact) {
    fun requireCurrent(state: CollectorState?): ArticleFact {
        val snapshot = state?.snapshot
        if (state == null || state.source.id != sourceId || state.source.version != sourceVersion ||
            !state.source.selected || !state.source.enabled || snapshot?.contentRevision != contentRevision ||
            (snapshot.payload as? CollectorPayload.Articles)?.items?.find { it.id == article.id } != article)
            throw ArticleReviewChanged()
        return article
    }
}

class ArticleReviewChanged : IllegalStateException("The reviewed article changed or is no longer selected")
