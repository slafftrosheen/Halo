#!/usr/bin/env bash
# Isolated transport check, not Room/SAF/Compose or a full Android build.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
KOTLINC="${KOTLINC:-$(command -v kotlinc)}"
KOTLIN_HOME="${KOTLIN_HOME:-$(cd "$(dirname "$(readlink -f "$KOTLINC")")/.." && pwd)}"
COROUTINES_JAR="${COROUTINES_JAR:-$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar}"
: "${ANDROID_API_JAR:?Set ANDROID_API_JAR to an executable Android API jar (e.g. Robolectric android-all), not android.jar stubs}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
# Reuse the real WorkspaceFile value shape; isolate its Room owner at a metadata boundary.
python3 - "$ROOT" "$TMP" <<'PY'
from pathlib import Path
import sys
root, out = map(Path, sys.argv[1:])
s = (root/'app/src/main/java/com/halo/app/data/workspace/WorkspaceFileRepository.kt').read_text()
a = s.index('data class WorkspaceFile('); b = s.index('\ninterface WorkspaceFileRepository', a)
(out/'Types.kt').write_text('''package com.halo.app.data.workspace
import android.net.Uri
import com.halo.app.data.notebook.WorkspaceObjectId
'''+s[a:b]+'''
enum class FilePermissionMode { PERSISTABLE_URI, OWNED_COPY, READ_ONLY }
enum class ContentOwnership { HALO_OWNED, EXTERNAL_REFERENCE }
enum class WorkspaceFileAvailability { AVAILABLE, NEEDS_RELINK, MISSING, OWNED_LOCAL }
data class WorkspaceFileVersion(val objectId: String, val version: String)
class WorkspaceFileVersions { suspend fun current(source: WorkspaceFileVersion): WorkspaceFile = error("Room boundary not exercised") }
''')
(out/'Id.kt').write_text('package com.halo.app.data.notebook\n@JvmInline value class WorkspaceObjectId(val value: String)\n')
PY
"$KOTLINC" "$TMP"/*.kt "$ROOT/app/src/main/java/com/halo/app/data/workspace/WorkspaceExternalExport.kt" \
  "$ROOT/tools/wearables/WorkspaceExportChecks.kt" -cp "$ANDROID_API_JAR:$COROUTINES_JAR" -include-runtime -d "$TMP/checks.jar"
java -cp "$TMP/checks.jar:$ANDROID_API_JAR:$COROUTINES_JAR" checks.WorkspaceExportChecksKt
