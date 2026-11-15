<#
.SYNOPSIS
  Збирає Read With Kid разом з мовними моделями (asset packs model_uk, model_de)
  і встановлює на підключений по USB телефон через bundletool local testing.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\install-on-phone.ps1
  powershell -ExecutionPolicy Bypass -File scripts\install-on-phone.ps1 -Release
  powershell -ExecutionPolicy Bypass -File scripts\install-on-phone.ps1 -Serial R58M12ABCDE

.PARAMETER Release
  Зібрати release (R8). APK підписуються debug-ключем bundletool — для перевірки на телефоні цього досить.

.PARAMETER Serial
  Серійний номер телефону з `adb devices`, якщо підключено більше одного.
#>
param(
    [switch]$Release,
    [string]$Serial
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# --- Android SDK / adb ---
$sdk = $env:ANDROID_HOME
if (-not $sdk -and (Test-Path local.properties)) {
    $m = Select-String -Path local.properties -Pattern '^sdk\.dir=(.*)$'
    if ($m) { $sdk = $m.Matches[0].Groups[1].Value -replace '\\:', ':' -replace '\\\\', '\' }
}
if (-not $sdk) { $sdk = "$env:LOCALAPPDATA\Android\Sdk" }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
if (-not (Test-Path $adb)) {
    throw "adb не знайдено: $adb. Відкрийте проєкт в Android Studio (вона створить local.properties) або встановіть Android SDK Platform-Tools через SDK Manager."
}

# --- Java (JDK з Android Studio, якщо JAVA_HOME не задано) ---
if (-not $env:JAVA_HOME) {
    foreach ($c in "$env:ProgramFiles\Android\Android Studio\jbr", "$env:LOCALAPPDATA\Programs\Android Studio\jbr") {
        if (Test-Path "$c\bin\java.exe") { $env:JAVA_HOME = $c; break }
    }
}
if (-not $env:JAVA_HOME) { throw "Java не знайдено. Задайте JAVA_HOME (наприклад, на папку jbr в Android Studio)." }
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'

# --- Телефон ---
$devices = @(& $adb devices | Select-String "`tdevice$" | ForEach-Object { ($_ -split "`t")[0] })
if ($devices.Count -eq 0) {
    throw "Телефон не знайдено. Перевірте кабель, «Налагодження по USB» і дозвіл на телефоні, потім: & '$adb' devices"
}
if (-not $Serial) {
    if ($devices.Count -gt 1) { throw "Підключено кілька телефонів: $($devices -join ', '). Вкажіть один: -Serial <номер>" }
    $Serial = $devices[0]
}
Write-Host "Телефон: $Serial"

# --- Мовні моделі (Apache 2.0) ---
$models = @(
    @{ Dir = 'model_uk\src\main\assets'; File = 'vosk-model-small-uk-v3-nano.zip' },
    @{ Dir = 'model_de\src\main\assets'; File = 'vosk-model-small-de-0.15.zip' }
)
foreach ($m in $models) {
    $dst = Join-Path $m.Dir $m.File
    if (-not (Test-Path $dst)) {
        New-Item -ItemType Directory -Force $m.Dir | Out-Null
        Write-Host "Завантаження $($m.File)..."
        Invoke-WebRequest "https://alphacephei.com/vosk/models/$($m.File)" -OutFile "$dst.part"
        Move-Item "$dst.part" $dst
    }
}

# --- bundletool ---
New-Item -ItemType Directory -Force build | Out-Null
$bundletool = 'build\bundletool.jar'
if (-not (Test-Path $bundletool)) {
    Write-Host 'Завантаження bundletool...'
    $rel = Invoke-RestMethod 'https://api.github.com/repos/google/bundletool/releases/latest'
    $url = ($rel.assets | Where-Object { $_.name -like 'bundletool-all-*.jar' } | Select-Object -First 1).browser_download_url
    Invoke-WebRequest $url -OutFile $bundletool
}

# --- Збірка AAB ---
$variant = if ($Release) { 'release' } else { 'debug' }
$task = if ($Release) { 'bundleRelease' } else { 'bundleDebug' }
& .\gradlew.bat $task
if ($LASTEXITCODE -ne 0) { throw "$task завершився помилкою" }
$aab = "build\outputs\bundle\$variant\Read-With-Kid-$variant.aab"
$apks = "build\read-with-kid-$variant.apks"
if (Test-Path $apks) { Remove-Item $apks }

# --- Встановлення з asset packs (local testing) ---
& $java -jar $bundletool build-apks --bundle=$aab --output=$apks --local-testing --connected-device "--adb=$adb" "--device-id=$Serial"
if ($LASTEXITCODE -ne 0) { throw 'bundletool build-apks завершився помилкою' }
& $java -jar $bundletool install-apks --apks=$apks "--adb=$adb" "--device-id=$Serial"
if ($LASTEXITCODE -ne 0) {
    throw "Встановлення не вдалося. Якщо помилка INSTALL_FAILED_UPDATE_INCOMPATIBLE — видаліть стару версію: & '$adb' -s $Serial uninstall com.gefolgwar.readwithkid"
}
Write-Host "Готово: Read With Kid ($variant) встановлено на $Serial разом з мовними моделями."
