package com.halo.app.data.search

import androidx.appsearch.annotation.Document
import androidx.appsearch.app.AppSearchSchema

@Document
data class WorkspaceSearchDocument(
    @Document.Namespace val namespace: String = "workspace",
    @Document.Id val id: String,
    @Document.StringProperty(indexingType = AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES) val title: String,
    @Document.StringProperty(indexingType = AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES) val content: String,
    @Document.StringProperty val type: String,
    @Document.LongProperty val updatedAt: Long
)
