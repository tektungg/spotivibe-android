# Build APK release dan taruh di dist/ dengan nama berversi.
#
# Ada karena langkah "copy APK ke dist/" dikerjakan manual, dan sekali salah
# tujuan APK 0.6.0 mendarat di folder induk sementara 0.5.2 ada di dist/.
# Nama berkas dan folder tujuan diturunkan dari build.gradle.kts, bukan diketik.
#
# Pakai:
#   powershell -ExecutionPolicy Bypass -File scripts\release.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\release.ps1 -SkipTests

param(
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'
$akar = Split-Path -Parent $PSScriptRoot
Set-Location $akar

$gradleFile = Join-Path $akar 'app\build.gradle.kts'
$isi = Get-Content $gradleFile -Raw

$mVersi = [regex]::Match($isi, 'versionName\s*=\s*"([^"]+)"')
$mKode  = [regex]::Match($isi, 'versionCode\s*=\s*(\d+)')
if (-not $mVersi.Success -or -not $mKode.Success) {
    throw "versionName/versionCode tidak ketemu di $gradleFile"
}
$versi = $mVersi.Groups[1].Value
$kode  = $mKode.Groups[1].Value
Write-Host "Versi $versi (code $kode)"

# Keystore rilis tidak masuk repo. Tanpa ini Gradle diam-diam jatuh ke debug
# signing, dan APK-nya tidak bisa dipasang menimpa yang sudah terinstall.
$props = Join-Path $akar 'keystore.properties'
if (-not (Test-Path $props)) {
    throw "keystore.properties tidak ada. Salin dari keystore.properties.example lalu isi."
}

# Gradle butuh JAVA_HOME dan environment ini tidak menyetelnya. Tanpa blok ini
# script gagal di baris pertama gradlew dengan pesan yang tidak menyebut Android
# Studio sama sekali. JDK bawaan Android Studio dipakai supaya versinya sama
# dengan yang dipakai IDE.
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $kandidat = @(
        'C:\Program Files\Android\Android Studio\jbr',
        'C:\Program Files\Android\Android Studio\jre',
        "$env:LOCALAPPDATA\Programs\Android Studio\jbr"
    )
    $jdk = $kandidat | Where-Object { Test-Path (Join-Path $_ 'bin\java.exe') } | Select-Object -First 1
    if (-not $jdk) {
        throw "JAVA_HOME tidak diset dan JDK Android Studio tidak ketemu. Dicari di:`n  " + ($kandidat -join "`n  ")
    }
    $env:JAVA_HOME = $jdk
    Write-Host "JAVA_HOME -> $jdk"
}

if (-not $SkipTests) {
    Write-Host 'Menjalankan test...'
    & (Join-Path $akar 'gradlew.bat') testDebugUnitTest --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Test gagal. Rilis dibatalkan.' }
}

Write-Host 'Build release...'
& (Join-Path $akar 'gradlew.bat') assembleRelease --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Build release gagal.' }

# Versi dibaca ULANG dari output Gradle, bukan dari yang tadi diparse. Kalau
# build memakai varian lain, ketidakcocokannya ketahuan di sini, bukan setelah
# APK dengan nama salah sudah dibagikan.
$metaPath = Join-Path $akar 'app\build\outputs\apk\release\output-metadata.json'
$meta = (Get-Content $metaPath -Raw | ConvertFrom-Json).elements[0]
if ($meta.versionName -ne $versi) {
    throw "APK bilang $($meta.versionName), build.gradle.kts bilang $versi."
}

$dist = Join-Path $akar 'dist'
if (-not (Test-Path $dist)) { New-Item -ItemType Directory -Path $dist | Out-Null }

$sumber = Join-Path $akar "app\build\outputs\apk\release\$($meta.outputFile)"
$tujuan = Join-Path $dist "spotivibe-$versi.apk"
Copy-Item $sumber $tujuan -Force

$mb = [math]::Round((Get-Item $tujuan).Length / 1MB, 1)
Write-Host ''
Write-Host "OK  $tujuan  ($mb MB)"
Write-Host ''
Write-Host 'Install:'
Write-Host "  adb install -r `"$tujuan`""
