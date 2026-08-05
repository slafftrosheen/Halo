#!/bin/bash
for file in $(find . -name "build.gradle.kts" -not -path "./app/*" -not -path "./settings.gradle.kts" -not -path "./build.gradle.kts"); do
    echo "}" >> "$file"
done
