#!/bin/bash

# Create directories
mkdir -p runtime/kernel runtime/events runtime/commands runtime/queries runtime/scheduler runtime/registry runtime/lifecycle
mkdir -p sdk/runtime-sdk sdk/driver-sdk sdk/provider-sdk sdk/plugin-sdk
mkdir -p engines/voice engines/memory engines/workflow engines/provider engines/device engines/context engines/privacy engines/automation engines/notification
mkdir -p drivers/heycyan providers/llamacpp
mkdir -p libraries/common libraries/logging libraries/testing

# Base build.gradle.kts content for a Kotlin library (Android library)
cat << 'INNER_EOF' > base_build.gradle.kts
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.halo.placeholder"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
INNER_EOF

# Function to setup module
setup_module() {
    local path=$1
    local name=$2
    local namespace="com.halo.${name//-/.}"
    
    mkdir -p "$path/src/main/java/${namespace//./\/}"
    sed "s/com.halo.placeholder/$namespace/g" base_build.gradle.kts > "$path/build.gradle.kts"
}

setup_module "runtime/kernel" "runtime.kernel"
setup_module "runtime/events" "runtime.events"
setup_module "runtime/commands" "runtime.commands"
setup_module "runtime/queries" "runtime.queries"
setup_module "runtime/scheduler" "runtime.scheduler"
setup_module "runtime/registry" "runtime.registry"
setup_module "runtime/lifecycle" "runtime.lifecycle"

setup_module "sdk/runtime-sdk" "sdk.runtime"
setup_module "sdk/driver-sdk" "sdk.driver"
setup_module "sdk/provider-sdk" "sdk.provider"
setup_module "sdk/plugin-sdk" "sdk.plugin"

setup_module "engines/voice" "engines.voice"
setup_module "engines/memory" "engines.memory"
setup_module "engines/workflow" "engines.workflow"
setup_module "engines/provider" "engines.provider"
setup_module "engines/device" "engines.device"
setup_module "engines/context" "engines.context"
setup_module "engines/privacy" "engines.privacy"
setup_module "engines/automation" "engines.automation"
setup_module "engines/notification" "engines.notification"

setup_module "drivers/heycyan" "drivers.heycyan"
setup_module "providers/llamacpp" "providers.llamacpp"

setup_module "libraries/common" "libraries.common"
setup_module "libraries/logging" "libraries.logging"
setup_module "libraries/testing" "libraries.testing"

rm base_build.gradle.kts
