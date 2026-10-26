package com.halo.app.ui.theme

/** Apply failed; true means at least one compensating disk write was not acknowledged. */
class AppearanceApplyException(val rollbackUncertain: Boolean, cause: Exception) :
    IllegalStateException(if (rollbackUncertain) "Could not confirm appearance rollback" else "Could not apply appearance", cause)
