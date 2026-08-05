#!/bin/bash
for file in $(find . -name "build.gradle.kts" -not -path "./app/*" -not -path "./settings.gradle.kts" -not -path "./build.gradle.kts"); do
    sed -i '/id("org.jetbrains.kotlin.android")/d' "$file"
done
