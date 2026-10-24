#requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$ToolchainRoot,
    [Parameter(Mandatory)][string]$Sysroot,
    [Parameter(Mandatory)][string]$JdkRoot,
    [Parameter(Mandatory)][string]$LinuxJniDirectory,
    [string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
foreach ($name in @('ToolchainRoot','Sysroot','JdkRoot','LinuxJniDirectory')) {
    Set-Variable -Name $name -Value ([IO.Path]::GetFullPath((Get-Variable -Name $name -ValueOnly)))
}
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $repoRoot ('.artifacts\ink-linux-' + [guid]::NewGuid().ToString('N')) }
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if ((Test-Path -LiteralPath $OutputDirectory) -and @(Get-ChildItem -LiteralPath $OutputDirectory -Force).Count) {
    throw 'Choose a fresh empty evidence directory; retain previous failures'
}
$compiler = Join-Path $ToolchainRoot 'bin\clang++.exe'
$inspect = Join-Path $ToolchainRoot 'bin\llvm-readobj.exe'
$source = Join-Path $repoRoot 'host-ink\native\pen_input.cpp'
foreach ($path in @($compiler,$inspect,$source,(Join-Path $JdkRoot 'include\jni.h'),
    (Join-Path $LinuxJniDirectory 'jni_md.h'),(Join-Path $Sysroot 'usr\include\linux\input.h'),
    (Join-Path $Sysroot 'usr\lib\gcc\x86_64-linux-gnu\12\crtbeginS.o'))) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required cross-build input absent: $path" }
}
[IO.Directory]::CreateDirectory($OutputDirectory) | Out-Null
function Hash([string]$Path) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
$library = Join-Path $OutputDirectory 'libhalo_ink_pen.so'
$arguments = @('--target=x86_64-linux-gnu',('--sysroot=' + $Sysroot),
    ('--gcc-toolchain=' + (Join-Path $Sysroot 'usr')), '-stdlib=libstdc++', '--rtlib=libgcc', '-fuse-ld=lld',
    '-std=c++17','-Wall','-Wextra','-Werror','-fPIC','-shared','-Wl,--no-undefined',
    ('-I' + (Join-Path $JdkRoot 'include')),('-I' + $LinuxJniDirectory),$source,'-o',$library)
$record = [ordered]@{schema=1;scope='LINUX_X86_64_CROSS_COMPILE_LINK_AND_EXPORT_ONLY';status='INCOMPLETE';
    sourceRevision=(& git -C $repoRoot rev-parse HEAD).Trim();capturedAt=(Get-Date -Format o);
    sourceSha256=(Hash $source);compilerSha256=(Hash $compiler);jniHeaderSha256=(Hash (Join-Path $JdkRoot 'include\jni.h'));
    linuxJniHeaderSha256=(Hash (Join-Path $LinuxJniDirectory 'jni_md.h'));compilerArguments=$arguments;
    nativeLoad='NOT_TESTED';nativeLoop='NOT_TESTED';physicalPen='NOT_TESTED';secondHost='NOT_TESTED';
    piArm64='NOT_TESTED';activation='CLOSED_PENDING_QUALIFICATION'}
try {
    (& $compiler --version 2>&1 | Out-String) | Set-Content -Encoding utf8 -LiteralPath (Join-Path $OutputDirectory 'compiler.txt')
    if ($LASTEXITCODE -ne 0) { throw 'Compiler version check failed' }
    (& $compiler @arguments 2>&1 | Out-String) | Set-Content -Encoding utf8 -LiteralPath (Join-Path $OutputDirectory 'compile.txt')
    if ($LASTEXITCODE -ne 0) { throw 'Linux cross-compilation failed; inspect compile.txt' }
    $inspection = & $inspect --file-headers --dynamic-table --dyn-symbols $library 2>&1 | Out-String
    $inspection | Set-Content -Encoding utf8 -LiteralPath (Join-Path $OutputDirectory 'inspection.txt')
    if ($LASTEXITCODE -ne 0 -or $inspection -notmatch 'elf64-x86-64' -or $inspection -notmatch 'SharedObject') {
        throw 'Expected a linked Linux x86_64 shared object'
    }
    foreach ($entry in @('prepareNative','readWindows','readLinux','stopNative')) {
        if ($inspection -notmatch ('\bJava_com_halo_host_ink_DesktopPenSource_' + $entry + '\b')) { throw "Missing JNI export: $entry" }
    }
    $record.status = 'PASS'
    $record.librarySha256 = Hash $library
    $record.libraryBytes = (Get-Item -LiteralPath $library).Length
} catch {
    $record.status = 'FAIL'; $record.error = $_.Exception.Message; throw
} finally {
    ConvertTo-Json -InputObject $record -Depth 8 | Set-Content -Encoding utf8 -LiteralPath (Join-Path $OutputDirectory 'build-result.json')
}
Write-Output "Linux x86_64 cross-build/link/exports PASS. Linux JVM load, native loop and physical input remain NOT_TESTED. Evidence: $OutputDirectory"
