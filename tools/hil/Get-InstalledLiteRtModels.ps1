<# Read-only installed inventory, verified by Halo's manager. Never reads model binaries into the host. #>
[CmdletBinding()]
param(
    [string] $Serial = "",
    [string] $Package = "com.aistudio.braintool.xkqzps",
    [string] $Adb = "adb",
    [string] $OutputDir = ("installed-litert-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
)
$ErrorActionPreference = "Stop"
if ($Serial -notmatch '^[A-Za-z0-9._:-]+$') { throw "Select an explicit attached device serial with -Serial." }
if ($Serial -like "emulator-*") { throw "Installed qualification requires a physical device." }
if ($Package -notmatch '^[A-Za-z0-9_.]+$') { throw "Unsafe package name." }
$null = Get-Command $Adb -ErrorAction Stop
if (Test-Path -LiteralPath (Join-Path $OutputDir "installed-models.json")) { throw "Use a fresh inventory OutputDir; snapshots are immutable." }
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
function Write-StoppedInventory([string] $Status, [string] $Reason) {
    $stopped = [pscustomobject]@{ schema=1; serial=$Serial; package=$Package; status=$Status; reason=$Reason; models=@(); partials=@() }
    $stopped | ConvertTo-Json -Depth 10 | Set-Content (Join-Path $OutputDir "installed-models.json") -Encoding utf8
    return $stopped
}
$devices = (& $Adb devices -l | Out-String)
$ready = @($devices -split "`r?`n" | Where-Object { $_ -match ('^' + [regex]::Escape($Serial) + '\s+device(?:\s|$)') })
if ($ready.Count -ne 1) {
    $unavailable = [pscustomobject]@{ schema=1; serial=$Serial; package=$Package; status="NOT_RUN_DEVICE_UNAVAILABLE"; models=@(); partials=@() }
    $unavailable | ConvertTo-Json -Depth 10 | Set-Content (Join-Path $OutputDir "installed-models.json") -Encoding utf8
    return $unavailable
}
$virtual = (& $Adb -s $Serial shell getprop ro.kernel.qemu | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $virtual -eq "1") { throw "Physical device identity could not be confirmed." }
$listing = & $Adb -s $Serial shell run-as $Package ls -1 no_backup/models 2>&1
if ($LASTEXITCODE -ne 0) { throw "Read-only run-as inventory failed; stop and check the debug package. No root or private-data copy is attempted." }
$listing | Set-Content (Join-Path $OutputDir "filenames.txt") -Encoding utf8
$source = (& git rev-parse HEAD | Out-String).Trim()
if (& git status --porcelain) { $source += "-dirty" }
$apkEvidence = @()
$repoRoot = Join-Path $PSScriptRoot "../.."
foreach ($apk in @("app/build/outputs/apk/debug/app-debug.apk", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")) {
    $apkPath = Join-Path $repoRoot $apk
    if (-not (Test-Path -LiteralPath $apkPath)) { throw "Build both debug APKs before installed inventory verification." }
    $builtHash = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $installedPackage = if ($apk -like "*androidTest*") { "$Package.test" } else { $Package }
    $installedPaths = @(& $Adb -s $Serial shell pm path $installedPackage | Where-Object { $_ -match '^package:.*/base\.apk$' })
    if ($LASTEXITCODE -ne 0 -or $installedPaths.Count -ne 1) { throw "Cannot identify the installed debug APK; stop before inventory verification." }
    $installedPath = $installedPaths[0].Substring(8)
    if ($installedPath -notmatch '^/data/app/[A-Za-z0-9_./~+=-]+/base\.apk$') { throw "Unexpected installed APK path; no shell expansion is allowed." }
    $hashOutput = (& $Adb -s $Serial shell sha256sum $installedPath | Out-String)
    if ($LASTEXITCODE -ne 0 -or $hashOutput -notmatch '^([a-fA-F0-9]{64})\s+') { throw "Cannot verify the installed APK fingerprint." }
    if ($Matches[1].ToLowerInvariant() -ne $builtHash) { throw "Installed APK differs from the built APK. Install the matching debug app and test APKs before retrying." }
    $apkEvidence += [pscustomobject]@{ package=$installedPackage; sha256=$builtHash }
    $source += "-" + $builtHash
}
$runner = "$Package.test/androidx.test.runner.AndroidJUnitRunner"
$output = & $Adb -s $Serial shell am instrument -w -r -e class com.halo.app.audio.InstalledLiteRtInventoryTest#exportVerifiedInventory -e sourceStamp $source $runner 2>&1 | Tee-Object -FilePath (Join-Path $OutputDir "inventory-runner.txt")
$runnerExit = $LASTEXITCODE
$text = $output | Out-String
$text | Set-Content (Join-Path $OutputDir "inventory-runner.txt") -Encoding utf8
if ($runnerExit -ne 0 -or $text -notmatch 'INSTRUMENTATION_CODE:\s*-1' -or $text -notmatch 'OK\s*\(1\s*test\)') {
    return (Write-StoppedInventory "RUNNER_INTERRUPTED" "INVENTORY_RUNNER_DID_NOT_COMPLETE")
}
$json = (& $Adb -s $Serial exec-out run-as $Package cat files/installed-litert-models.json | Out-String)
if ($LASTEXITCODE -ne 0) { return (Write-StoppedInventory "RUNNER_INTERRUPTED" "VERIFIED_SNAPSHOT_COULD_NOT_BE_READ") }
$inventory = $json | ConvertFrom-Json
if ($inventory.status -ne "INVENTORY_VERIFIED" -or $inventory.package -ne $Package -or $inventory.sourceStamp -ne $source) {
    return (Write-StoppedInventory "RUNNER_INTERRUPTED" "SNAPSHOT_PACKAGE_OR_SOURCE_MISMATCH")
}
$inventory | Add-Member -NotePropertyName installedApks -NotePropertyValue $apkEvidence -Force
$inventory | Add-Member -NotePropertyName serial -NotePropertyValue $Serial -Force
$inventory | ConvertTo-Json -Depth 15 | Set-Content (Join-Path $OutputDir "installed-models.json") -Encoding utf8
return $inventory
