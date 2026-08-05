#!/bin/bash
for file in $(find . -name "build.gradle.kts" -not -path "./app/*" -not -path "./settings.gradle.kts"); do
    namespace=$(grep "namespace =" "$file" | sed 's/.*"\(.*\)".*/\1/')
    deps=$(grep -A 100 "dependencies {" "$file" | tail -n +2)
    
    cat << INN > "$file"
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "$namespace"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
    }
}

dependencies {
$deps
INN
    echo "Fixed $file"
done
