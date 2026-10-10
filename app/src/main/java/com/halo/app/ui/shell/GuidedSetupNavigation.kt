package com.halo.app.ui.shell

import androidx.navigation.NavController

/** Return to the same entry: child setup/review must not discard the guide's saved checks. */
fun NavController.navigateToGuidedSetup() {
    if(currentDestination?.route == "guided_setup") return
    if(!popBackStack("guided_setup", false)) navigate("guided_setup") { launchSingleTop = true }
}
