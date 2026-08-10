# Pasang secret GitHub Actions dari berkas lokal yang gitignored.
#
# Nilai rahasia dibaca langsung dari keystore.properties, local.properties, dan
# release.jks lalu dikirim ke GitHub. Tidak ada yang perlu diketik ulang, jadi
# password tidak pernah singgah di clipboard, chat, atau riwayat shell.
#
# Sekali saja, kecuali keystore atau client ID berganti.
#
# Pakai:
#   powershell -ExecutionPolicy Bypass -File scripts\setup-ci-secrets.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\setup-ci-secrets.ps1 -WhatIf

param(
    [switch]$WhatIf
)

$ErrorActionPreference = 'Stop'
$akar = Split-Path -Parent $PSScriptRoot
Set-Location $akar

function Baca-Properties($path) {
    $h = @{}
    foreach ($baris in Get-Content $path) {
        $b = $baris.Trim()
        if ($b -and -not $b.StartsWith('#') -and $b.Contains('=')) {
            $i = $b.IndexOf('=')
            $h[$b.Substring(0, $i).Trim()] = $b.Substring($i + 1).Trim()
        }
    }
    return $h
}

foreach ($wajib in @('keystore.properties', 'local.properties')) {
    if (-not (Test-Path (Join-Path $akar $wajib))) { throw "$wajib tidak ada." }
}

$ks = Baca-Properties (Join-Path $akar 'keystore.properties')
$lp = Baca-Properties (Join-Path $akar 'local.properties')

# storeFile relatif terhadap root project, sama seperti rootProject.file() di
# build.gradle.kts.
$jks = Join-Path $akar $ks['storeFile']
if (-not (Test-Path $jks)) { throw "Keystore tidak ketemu di $jks" }

$clientId = $lp['spotify.clientId']
if ([string]::IsNullOrWhiteSpace($clientId)) {
    throw 'spotify.clientId kosong di local.properties.'
}

# Repo diambil dari remote git, bukan diketik, supaya secret tidak pernah
# terkirim ke repo yang salah. Owner diambil dari PATH remote, bukan dari
# hostname, karena host di sini adalah alias SSH (`github-personal`) yang tidak
# sama dengan nama akun GitHub.
$remote = git remote get-url origin
if ($remote -notmatch '[:/]([^/:]+)/([^/]+?)(\.git)?$') { throw "Remote tidak dikenali: $remote" }
$repo = "$($Matches[1])/$($Matches[2])"
Write-Host "Repo   : $repo"

# gh dipakai bersama untuk semua repo, jadi akun aktif harus dipastikan cocok
# dengan owner repo sebelum secret dikirim.
$status = (gh auth status 2>&1) -join "`n"
$owner = $repo.Split('/')[0]
if ($status -notmatch "account $owner[^\r\n]*\r?\n[^\r\n]*Active account: true") {
    Write-Warning "Akun gh aktif mungkin bukan '$owner'."
    Write-Host $status
    $lanjut = Read-Host "Lanjut kirim secret ke $repo? (ketik ya)"
    if ($lanjut -ne 'ya') { throw 'Dibatalkan.' }
}

$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($jks))

$secrets = [ordered]@{
    KEYSTORE_BASE64   = $b64
    KEYSTORE_PASSWORD = $ks['storePassword']
    KEY_ALIAS         = $ks['keyAlias']
    KEY_PASSWORD      = $ks['keyPassword']
    SPOTIFY_CLIENT_ID = $clientId
}

foreach ($nama in $secrets.Keys) {
    $nilai = $secrets[$nama]
    if ([string]::IsNullOrWhiteSpace($nilai)) { throw "$nama kosong." }

    if ($WhatIf) {
        Write-Host "[WhatIf] $nama  ($($nilai.Length) char)"
        continue
    }

    # Lewat stdin, bukan argumen, supaya nilainya tidak muncul di daftar proses.
    $nilai | gh secret set $nama --repo $repo
    if ($LASTEXITCODE -ne 0) { throw "Gagal memasang $nama" }
    Write-Host "OK  $nama"
}

if (-not $WhatIf) {
    Write-Host ''
    gh secret list --repo $repo
    Write-Host ''
    Write-Host 'Rilis berikutnya:'
    Write-Host '  git tag v0.6.0 ; git push origin v0.6.0'
}
