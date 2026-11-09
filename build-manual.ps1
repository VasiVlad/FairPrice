$ErrorActionPreference = 'Continue'

$src = $PSScriptRoot
$work = "$env:TEMP\up-build"
$root = "$env:LOCALAPPDATA\AndroidDev"
$sdk = "$root\sdk"
$bt = "$sdk\build-tools\33.0.2"
$plat = "$sdk\platforms\android-33\android.jar"
$jdk = "$root\jdk"
$adb = "$env:LOCALAPPDATA\Android\platform-tools\adb.exe"
$dev = "192.168.1.59:5555"

$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$env:Path"

Write-Output "src=$src"
Write-Output "work=$work"

Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$work\gen" | Out-Null
New-Item -ItemType Directory -Force "$work\classes" | Out-Null
New-Item -ItemType Directory -Force "$work\dex" | Out-Null

Copy-Item "$src\app\src\main\res" "$work\res" -Recurse -Force
Copy-Item "$src\app\src\main\java" "$work\java" -Recurse -Force
Copy-Item "$src\app\src\main\AndroidManifest.xml" "$work\AndroidManifest.xml" -Force

Write-Output "=== AAPT2 COMPILE ==="
& "$bt\aapt2.exe" compile --dir "$work\res" -o "$work\res.zip"
Write-Output "exit=$LASTEXITCODE"

Write-Output "=== AAPT2 LINK ==="
& "$bt\aapt2.exe" link -o "$work\app-unsigned.apk" -I $plat `
    --manifest "$work\AndroidManifest.xml" `
    --java "$work\gen" --min-sdk-version 26 --target-sdk-version 33 `
    --version-code 22 --version-name 0.22 "$work\res.zip"
Write-Output "exit=$LASTEXITCODE"

Write-Output "=== JAVAC ==="
$srcs = @(Get-ChildItem "$work\java" -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$rs = @(Get-ChildItem "$work\gen" -Recurse -Filter *.java | ForEach-Object { $_.FullName })
Write-Output "java sources: $($srcs.Count), generated: $($rs.Count)"
& "$jdk\bin\javac.exe" -encoding UTF-8 -source 8 -target 8 -Xlint:-options -classpath $plat -d "$work\classes" $srcs $rs
Write-Output "exit=$LASTEXITCODE"
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Output "=== D8 ==="
$classes = @(Get-ChildItem "$work\classes" -Recurse -Filter *.class | ForEach-Object { $_.FullName })
Write-Output "class files: $($classes.Count)"
& "$jdk\bin\java.exe" -cp "$bt\lib\d8.jar" com.android.tools.r8.D8 --lib $plat --min-api 26 --output "$work\dex" $classes
Write-Output "exit=$LASTEXITCODE"
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Output "=== PACK DEX ==="
Push-Location "$work\dex"
& "$jdk\bin\jar.exe" uf "$work\app-unsigned.apk" classes.dex
Pop-Location
Write-Output "exit=$LASTEXITCODE"

Write-Output "=== ZIPALIGN ==="
& "$bt\zipalign.exe" -f 4 "$work\app-unsigned.apk" "$work\app-aligned.apk"
Write-Output "exit=$LASTEXITCODE"

Write-Output "=== KEYSTORE ==="
$ksPersist = "$src\debug.keystore"
$ks = "$work\debug.keystore"
if (Test-Path $ksPersist) {
    Copy-Item $ksPersist $ks -Force
} else {
    & "$jdk\bin\keytool.exe" -genkeypair -keystore $ks -storepass android -keypass android `
        -alias debug -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=UnitPrice, O=Dev, C=RU" 2>&1 | Out-Null
    Copy-Item $ks $ksPersist -Force
}
Write-Output "keystore: $(Test-Path $ks)"

Write-Output "=== SIGN ==="
& "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:android --key-pass pass:android `
    --out "$work\app-debug.apk" "$work\app-aligned.apk"
Write-Output "exit=$LASTEXITCODE"

Write-Output "=== RESULT ==="
Get-Item "$work\app-debug.apk" -ErrorAction SilentlyContinue | Select-Object FullName,Length | Format-List | Out-String
& "$bt\apksigner.bat" verify --print-certs "$work\app-debug.apk" 2>&1 | Select-Object -First 4 | Out-String

Write-Output "=== INSTALL ==="
if (Test-Path "$work\app-debug.apk") {
    & $adb -s $dev install -r "$work\app-debug.apk" 2>&1 | Out-String
}

Write-Output "=== COPY APK TO PROJECT ==="
if (Test-Path "$work\app-debug.apk") {
    Copy-Item "$work\app-debug.apk" "$src\app-debug.apk" -Force
    Write-Output "copied to $src\app-debug.apk"
}

Write-Output "DONE"
