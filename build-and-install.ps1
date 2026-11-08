$ErrorActionPreference = 'Stop'

$root = "$env:LOCALAPPDATA\AndroidDev"
$proj = "C:\Users\Vlad\Documents\Проект по умолчанию\unitprice"
$adb = "$env:LOCALAPPDATA\Android\platform-tools\adb.exe"
$dev = "192.168.1.59:5555"

$env:JAVA_HOME = "$root\jdk"
$env:ANDROID_HOME = "$root\sdk"
$env:ANDROID_SDK_ROOT = "$root\sdk"
$env:Path = "$root\jdk\bin;$root\gradle\bin;$env:Path"

Write-Output "=== BUILD ==="
& "$root\gradle\bin\gradle.bat" -p $proj assembleDebug --no-daemon
if ($LASTEXITCODE -ne 0) { throw "build failed" }

$apk = "$proj\app\build\outputs\apk\debug\app-debug.apk"
Write-Output "=== APK: $apk ==="
Get-Item $apk | Select-Object Length,LastWriteTime | Format-List | Out-String

Write-Output "=== INSTALL ==="
& $adb -s $dev install -r $apk

Write-Output "=== ENABLE ACCESSIBILITY ==="
& $adb -s $dev shell "settings put secure enabled_accessibility_services ru.vlad.unitprice/ru.vlad.unitprice.UnitPriceService"
& $adb -s $dev shell "settings put secure accessibility_enabled 1"

Write-Output "=== LAUNCH ==="
& $adb -s $dev shell "monkey -p ru.vlad.unitprice -c android.intent.category.LAUNCHER 1"

Write-Output "DONE"
