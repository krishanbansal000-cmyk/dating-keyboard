# DatingCopilot Keyboard - Windows Build Script
# Run this in PowerShell as Administrator to install dependencies and build

param(
    [switch]$Setup,
    [switch]$Build,
    [switch]$Install
)

$PROJECT_DIR = "C:\Users\Krishan\repos\dating-keyboard"
$ANDROID_SDK = "$env:LOCALAPPDATA\Android\Sdk"

function Install-Dependencies {
    Write-Host "=== Installing Dependencies ===" -ForegroundColor Cyan

    # Check Java
    $java = Get-Command java -ErrorAction SilentlyContinue
    if (-not $java) {
        Write-Host "Installing JDK 17 via winget..." -ForegroundColor Yellow
        winget install EclipseAdoptium.Temurin.17.JDK --accept-source-agreements --accept-package-agreements
    } else {
        Write-Host "✓ Java found" -ForegroundColor Green
    }

    # Check Android SDK
    if (-not (Test-Path "$ANDROID_SDK\platform-tools\adb.exe")) {
        Write-Host "Installing Android SDK command-line tools..." -ForegroundColor Yellow
        $url = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
        $zip = "$env:TEMP\cmdline-tools.zip"
        Invoke-WebRequest -Uri $url -OutFile $zip
        Expand-Archive -Path $zip -DestinationPath "$ANDROID_SDK" -Force
        New-Item -ItemType Directory -Force -Path "$ANDROID_SDK\cmdline-tools\latest" | Out-Null
        Move-Item -Path "$ANDROID_SDK\cmdline-tools\*" -Destination "$ANDROID_SDK\cmdline-tools\latest\" -Force -ErrorAction SilentlyContinue
        Remove-Item $zip
        
        # Accept licenses and install required packages
        & "$ANDROID_SDK\cmdline-tools\latest\bin\sdkmanager.bat" --install "platform-tools" "platforms;android-34" "build-tools;34.0.0" --sdk_root=$ANDROID_SDK
    } else {
        Write-Host "✓ Android SDK found" -ForegroundColor Green
    }
}

function Build-Apk {
    Write-Host "=== Building APK ===" -ForegroundColor Cyan
    
    $env:ANDROID_HOME = $ANDROID_SDK
    $env:JAVA_HOME = (Get-Command java).Source -replace '\\bin\\java\.exe$', ''
    
    Set-Location $PROJECT_DIR
    
    # Generate Gradle wrapper if not exists
    if (-not (Test-Path "$PROJECT_DIR\gradlew.bat")) {
        # Download Gradle wrapper
        $url = "https://services.gradle.org/distributions/gradle-8.5-bin.zip"
        $zip = "$env:TEMP\gradle-wrapper.zip"
        Invoke-WebRequest -Uri $url -OutFile $zip
        Expand-Archive -Path $zip -DestinationPath "$env:TEMP\gradle" -Force
        
        & "$env:TEMP\gradle\gradle-8.5\bin\gradle.bat" wrapper --gradle-version=8.5
        Remove-Item $zip
        Remove-Item "$env:TEMP\gradle" -Recurse -Force
    }
    
    # Build
    .\gradlew.bat assembleDebug
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ Build successful!" -ForegroundColor Green
        Write-Host "APK at: $PROJECT_DIR\app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor Green
    } else {
        Write-Host "✗ Build failed" -ForegroundColor Red
    }
}

function Install-Apk {
    $apk = "$PROJECT_DIR\app\build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path $apk)) {
        Write-Host "APK not found. Run -Build first." -ForegroundColor Red
        return
    }
    
    $adb = "$ANDROID_SDK\platform-tools\adb.exe"
    if (-not (Test-Path $adb)) {
        Write-Host "ADB not found" -ForegroundColor Red
        return
    }
    
    Write-Host "Installing APK on connected device..." -ForegroundColor Cyan
    & $adb install -r $apk
    
    Write-Host "=== Setup Complete ===" -ForegroundColor Cyan
    Write-Host "1. Go to Android Settings → System → Languages & input → On-screen keyboard" -ForegroundColor Yellow
    Write-Host "2. Enable DatingCopilot" -ForegroundColor Yellow
    Write-Host "3. Open any app, tap a text field, switch to DatingCopilot keyboard" -ForegroundColor Yellow
}

# Main
if ($Setup) { Install-Dependencies }
if ($Build)  { Build-Apk }
if ($Install) { Install-Apk }

if (-not $Setup -and -not $Build -and -not $Install) {
    Write-Host @"
Usage: .\build.ps1 [-Setup] [-Build] [-Install]

  -Setup    Install JDK + Android SDK + dependencies
  -Build    Build the APK
  -Install  Install APK on connected Android device

Examples:
  .\build.ps1 -Setup          # First time: install everything
  .\build.ps1 -Build          # Build the APK
  .\build.ps1 -Install        # Install on phone via USB
  .\build.ps1 -Setup -Build   # Setup + build in one go
"@ -ForegroundColor Cyan
}
