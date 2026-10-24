#requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$ToolchainRoot,
    [Parameter(Mandatory)][string]$JdkRoot,
    [string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
if (-not $IsWindows) { throw 'This is the Windows x86_64 build/load check; Linux needs its own target validation' }
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$ToolchainRoot = [IO.Path]::GetFullPath($ToolchainRoot)
$JdkRoot = [IO.Path]::GetFullPath($JdkRoot)
if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path $repoRoot ('.artifacts\ink-windows-' + [guid]::NewGuid().ToString('N'))
}
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if ((Test-Path -LiteralPath $OutputDirectory) -and @(Get-ChildItem -LiteralPath $OutputDirectory -Force).Count) {
    throw 'Choose a fresh empty evidence directory; preserve older failures and outputs'
}
$compiler = Join-Path $ToolchainRoot 'bin\x86_64-w64-mingw32-clang++.exe'
$readObject = Join-Path $ToolchainRoot 'bin\llvm-readobj.exe'
$java = Join-Path $JdkRoot 'bin\java.exe'
$source = Join-Path $repoRoot 'host-ink\native\pen_input.cpp'
$probe = Join-Path $PSScriptRoot 'probes\LoadInkPen.java'
foreach ($path in @($compiler, $readObject, $java, $source, $probe,
    (Join-Path $JdkRoot 'include\jni.h'), (Join-Path $JdkRoot 'include\win32\jni_md.h'))) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required input absent: $path" }
}
[IO.Directory]::CreateDirectory($OutputDirectory) | Out-Null
function Hash([string]$Path) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
$dll = Join-Path $OutputDirectory 'halo_ink_pen.dll'
$arguments = @('-std=c++17', '-Wall', '-Wextra', '-Werror', '-shared', '-static',
    '-D_WIN32_WINNT=0x0602', '-DUNICODE', '-D_UNICODE', '-DNOMINMAX',
    ('-I' + (Join-Path $JdkRoot 'include')), ('-I' + (Join-Path $JdkRoot 'include\win32')),
    $source, '-o', $dll, '-luser32')
$record = [ordered]@{schema=1; scope='WINDOWS_X86_64_COMPILE_EXPORT_AND_JVM_LOAD_ONLY';
    status='INCOMPLETE'; sourceRevision=(& git -C $repoRoot rev-parse HEAD).Trim();
    capturedAt=(Get-Date -Format o); sourceSha256=(Hash $source); probeSha256=(Hash $probe);
    compilerSha256=(Hash $compiler); jniHeaderSha256=(Hash (Join-Path $JdkRoot 'include\jni.h'));
    compilerArguments=$arguments; activation='CLOSED_PENDING_QUALIFICATION'; physicalPen='NOT_TESTED';
    nativeLoop='NOT_TESTED'; secondHost='NOT_TESTED'; cmake='NOT_TESTED'}
$manifest = Join-Path $OutputDirectory 'build-result.json'
try {
    (& $compiler --version 2>&1 | Out-String) | Set-Content -Encoding utf8 -LiteralPath (Join-Path $OutputDirectory 'compiler.txt')
    if ($LASTEXITCODE -ne 0) { throw 'Compiler version check failed' }
    (& $java -version 2>&1 | Out-String) | Set-Content -Encoding utf8 -LiteralPath (Join-Path $OutputDirectory 'jvm.txt')
    if ($LASTEXITCODE -ne 0) { throw 'JVM version check failed' }
    (& $compiler @arguments 2>&1 | Out-String) | Set-Content -Encoding utf8 -LiteralPath (Join-Path $OutputDirectory 'compile.txt')
    if ($LASTEXITCODE -ne 0) { throw 'Windows native compilation failed; inspect compile.txt' }
    $inspection = & $readObject --file-headers --coff-exports --coff-imports $dll 2>&1 | Out-String
    $inspection | Set-Content -Encoding utf8 -LiteralPath (Join-Path $OutputDirectory 'inspection.txt')
    if ($LASTEXITCODE -ne 0 -or $inspection -notmatch 'COFF-x86-64') { throw 'Expected one Windows x86_64 DLL' }
    foreach ($entry in @('prepareNative', 'readWindows', 'readLinux', 'stopNative')) {
        if ($inspection -notmatch ('\bJava_com_halo_host_ink_DesktopPenSource_' + $entry + '\b')) {
            throw "Missing JNI export: $entry"
        }
    }
    $loaded = & $java --source 17 $probe $dll 2>&1 | Out-String
    $loaded | Set-Content -Encoding utf8 -LiteralPath (Join-Path $OutputDirectory 'jvm-load.txt')
    if ($LASTEXITCODE -ne 0 -or $loaded -notmatch '(?m)^NATIVE_LIBRARY_LOADED x86_64\s*$') { throw 'JVM native load failed' }
    $record.status = 'PASS'
    $record.dllSha256 = Hash $dll
    $record.dllBytes = (Get-Item -LiteralPath $dll).Length
} catch {
    $record.status = 'FAIL'
    $record.error = $_.Exception.Message
    throw
} finally {
    ConvertTo-Json -InputObject $record -Depth 8 | Set-Content -Encoding utf8 -LiteralPath $manifest
}
Write-Output "Windows x86_64 compile, JNI exports and JVM load PASS. Physical pen/native loop/second-host acceptance remains NOT_TESTED. Evidence: $OutputDirectory"
