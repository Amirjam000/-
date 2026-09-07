<#
.SYNOPSIS
اسکریپت آماده‌سازی و ساخت APK بدون نیاز به نرم‌افزار حجیم Android Studio
#>

param(
    [string]$BuildType = "debug"
)

Write-Host "=== شروع فرایند ساخت APK بدون Android Studio ===" -ForegroundColor Cyan

# ۱. بررسی جاوا
$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd) {
    Write-Host "خطا: جاوا روی سیستم شما نصب نیست. لطفاً JDK 17 را نصب کنید." -ForegroundColor Red
    exit 1
}
Write-Host "جاوا شناسایی شد: $($javaCmd.Source)" -ForegroundColor Green

# ۲. بررسی Android SDK
$androidHome = $env:ANDROID_HOME
if (-not $androidHome) {
    $androidHome = "$env:LOCALAPPDATA\Android\Sdk"
}

if (-not (Test-Path $androidHome)) {
    Write-Host "توجه: پکیج Android SDK در مسیر $androidHome یافت نشد." -ForegroundColor Yellow
    Write-Host "برای ساخت لوکال بدون اندروید استودیو، به ابزارهای خط فرمان Android Commandline Tools نیاز است." -ForegroundColor Yellow
    Write-Host "پیشنهاد فوق‌العاده: پروژه را روی ریپازیتوری GitHub قرار دهید تا اسکریپت GitHub Actions داخل پروژه ظرف ۲ دقیقه فایل APK را رایگان و بدون دانلود صدها مگابایت به شما تحویل دهد!" -ForegroundColor Cyan
} else {
    Write-Host "Android SDK در مسیر $androidHome شناسایی شد." -ForegroundColor Green
    $env:ANDROID_HOME = $androidHome
    
    if ($BuildType -eq "release") {
        Write-Host "در حال ساخت نسخه Release..." -ForegroundColor Cyan
        .\gradlew assembleRelease
    } else {
        Write-Host "در حال ساخت نسخه Debug..." -ForegroundColor Cyan
        .\gradlew assembleDebug
    }
}
