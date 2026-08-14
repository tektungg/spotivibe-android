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
    # Kedua varian. Yang berbeda di antara keduanya justru romanisasi, jadi
    # menguji satu saja membuat separuh matriksnya tidak pernah dijalankan.
    & (Join-Path $akar 'gradlew.bat') testFullDebugUnitTest testLiteDebugUnitTest --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Test gagal. Rilis dibatalkan.' }
}

Write-Host 'Build release kedua varian...'
& (Join-Path $akar 'gradlew.bat') assembleFullRelease assembleLiteRelease --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Build release gagal.' }

$dist = Join-Path $akar 'dist'
if (-not (Test-Path $dist)) { New-Item -ItemType Directory -Path $dist | Out-Null }

# Versi dibaca ULANG dari output Gradle, bukan dari yang tadi diparse. Kalau
# build memakai varian lain, ketidakcocokannya ketahuan di sini, bukan setelah
# APK dengan nama salah sudah dibagikan.
$hasil = @{}
foreach ($varian in @('full', 'lite')) {
    $metaPath = Join-Path $akar "app\build\outputs\apk\$varian\release\output-metadata.json"
    if (-not (Test-Path $metaPath)) { throw "Metadata varian $varian tidak ada di $metaPath" }
    $meta = (Get-Content $metaPath -Raw | ConvertFrom-Json).elements[0]

    # versionName varian lite membawa akhiran "-lite", jadi yang dicocokkan
    # adalah awalannya, bukan seluruh string.
    if (-not $meta.versionName.StartsWith($versi)) {
        throw "APK $varian bilang $($meta.versionName), build.gradle.kts bilang $versi."
    }

    $nama = if ($varian -eq 'lite') { "spotivibe-$versi-lite.apk" } else { "spotivibe-$versi.apk" }
    $sumber = Join-Path $akar "app\build\outputs\apk\$varian\release\$($meta.outputFile)"
    Copy-Item $sumber (Join-Path $dist $nama) -Force
    $hasil[$varian] = Join-Path $dist $nama
}

# Varian lite HARUS jauh lebih kecil. Kalau ukurannya mirip, kuromoji ikut
# terbawa dan seluruh guna varian ini hilang tanpa satu pun error.
$penuhB = (Get-Item $hasil['full']).Length
$ringanB = (Get-Item $hasil['lite']).Length
if ($ringanB -ge ($penuhB / 2)) {
    throw "APK lite $([math]::Round($ringanB/1MB,1)) MB tidak jauh lebih kecil dari full $([math]::Round($penuhB/1MB,1)) MB. Kuromoji kemungkinan ikut terbawa."
}

Write-Host ''
foreach ($varian in @('full', 'lite')) {
    $mb = [math]::Round((Get-Item $hasil[$varian]).Length / 1MB, 1)
    Write-Host ("OK  {0,-5} {1}  ({2} MB)" -f $varian, $hasil[$varian], $mb)
}
Write-Host ''
Write-Host 'Install:'
Write-Host "  adb install -r `"$($hasil['full'])`""
