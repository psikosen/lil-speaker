@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:execute
@rem Setup the command line

set WRAPPER_JAR=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
set NEED_WRAPPER_DOWNLOAD=true
if exist "%WRAPPER_JAR%" (
    for %%A in ("%WRAPPER_JAR%") do if %%~zA gtr 0 set NEED_WRAPPER_DOWNLOAD=false
)
if "%NEED_WRAPPER_DOWNLOAD%"=="true" (
    set PROPERTIES_FILE=%APP_HOME%\gradle\wrapper\gradle-wrapper.properties
    if not exist "%PROPERTIES_FILE%" (
        echo ERROR: gradle-wrapper.jar is missing and %PROPERTIES_FILE% could not be found.
        exit /b 1
    )

    where powershell >NUL 2>&1
    if %ERRORLEVEL% neq 0 (
        echo ERROR: gradle-wrapper.jar is missing and PowerShell is unavailable.
        echo Please install PowerShell or regenerate the wrapper with "gradle wrapper".
        exit /b 1
    )

    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
      "$ErrorActionPreference = 'Stop';" ^
      "$wrapperJar = [IO.Path]::GetFullPath('%WRAPPER_JAR%');" ^
      "$propertiesPath = [IO.Path]::GetFullPath('%PROPERTIES_FILE%');" ^
      "$match = Select-String -Path $propertiesPath -Pattern '^distributionUrl=' | Select-Object -First 1;" ^
      "$distributionUrl = $null;" ^
      "if ($match) { $distributionUrl = ($match.Line -replace '^distributionUrl=', '') -replace '\\', ''; $distributionUrl = $distributionUrl.Trim(); }" ^
      "$versionHint = $null;" ^
      "if ($distributionUrl -and ($distributionUrl -match 'gradle-([^/]+)-(bin|all)\.zip$')) { $versionHint = $matches[1]; } elseif ($distributionUrl -and ($distributionUrl -match 'gradle-([^/]+)\.zip$')) { $versionHint = $matches[1]; }" ^
      "Add-Type -AssemblyName System.IO.Compression.FileSystem;" ^
      "function Merge-JarFiles { param([string] $OutputPath, [string[]] $InputPaths)" ^
      "  if (Test-Path $OutputPath) { Remove-Item -Force $OutputPath }" ^
      "  $outputStream = [IO.File]::Open($OutputPath, [IO.FileMode]::Create, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None);" ^
      "  try {" ^
      "    $outputArchive = New-Object System.IO.Compression.ZipArchive($outputStream, [System.IO.Compression.ZipArchiveMode]::Create, $false);" ^
      "    foreach ($inputPath in $InputPaths) {" ^
      "      if (-not (Test-Path $inputPath)) { continue }" ^
      "      $inputStream = [IO.File]::OpenRead($inputPath);" ^
      "      try {" ^
      "        $inputArchive = New-Object System.IO.Compression.ZipArchive($inputStream, [System.IO.Compression.ZipArchiveMode]::Read);" ^
      "        foreach ($entry in $inputArchive.Entries) {" ^
      "          $outEntry = $outputArchive.CreateEntry($entry.FullName);" ^
      "          $inStream = $entry.Open();" ^
      "          $outStream = $outEntry.Open();" ^
      "          $inStream.CopyTo($outStream);" ^
      "          $inStream.Dispose();" ^
      "          $outStream.Dispose();" ^
      "        }" ^
      "        $inputArchive.Dispose();" ^
      "      } finally { $inputStream.Dispose(); }" ^
      "    }" ^
      "    $outputArchive.Dispose();" ^
      "  } finally { $outputStream.Dispose(); }" ^
      "}" ^
      "$candidateUrls = @();" ^
      "if ($versionHint) {" ^
      "  $candidateUrls += \"https://services.gradle.org/distributions/gradle-$versionHint-wrapper.jar\";" ^
      "  $candidateUrls += \"https://services.gradle.org/distributions/gradle-wrapper-$versionHint.jar\";" ^
      "  $candidateUrls += \"https://downloads.gradle.org/distributions/gradle-$versionHint-wrapper.jar\";" ^
      "  $candidateUrls += \"https://downloads.gradle.org/distributions/gradle-wrapper-$versionHint.jar\";" ^
      "  $candidateUrls += \"https://raw.githubusercontent.com/gradle/gradle/v$versionHint/gradle/wrapper/gradle-wrapper.jar\";" ^
      "  $candidateUrls += \"https://raw.githubusercontent.com/gradle/gradle/refs/tags/v$versionHint/gradle/wrapper/gradle-wrapper.jar\";" ^
      "}" ^
      "if ($distributionUrl) { $candidateUrls += $distributionUrl; }" ^
      "$downloaded = $false;" ^
      "foreach ($url in $candidateUrls) { if (-not $url) { continue }; try {" ^
      "    if ($url -like '*-wrapper.jar') {" ^
      "        Write-Host \"Gradle wrapper JAR missing; downloading from $url\";" ^
      "        Invoke-WebRequest -Uri $url -OutFile $wrapperJar -UseBasicParsing -ErrorAction Stop;" ^
      "        if ((Get-Item $wrapperJar).Length -gt 0) { $downloaded = $true; break }" ^
      "      } elseif ($url -like '*.zip') {" ^
      "        Write-Host \"Gradle wrapper JAR missing; assembling from $url\";" ^
      "        $tempDir = New-Item -ItemType Directory -Path ([IO.Path]::Combine([IO.Path]::GetTempPath(), 'gradle-wrapper-' + [Guid]::NewGuid().ToString())) -Force;" ^
      "        try {" ^
      "            $zipPath = Join-Path $tempDir 'gradle-distribution.zip';" ^
      "            Invoke-WebRequest -Uri $url -OutFile $zipPath -UseBasicParsing -ErrorAction Stop;" ^
      "            $zip = [IO.Compression.ZipFile]::OpenRead($zipPath);" ^
      "            $entry = $null;" ^
      "            if ($versionHint) {" ^
      "                $entry = $zip.GetEntry(\"gradle-$versionHint/lib/gradle-wrapper.jar\");" ^
      "            }" ^
      "            if (-not $entry) {" ^
      "                $entry = $zip.Entries | Where-Object { $_.FullName -match 'gradle-[^/]+/lib/gradle-wrapper.jar' } | Select-Object -First 1;" ^
      "            }" ^
      "            if ($entry) {" ^
      "                [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $wrapperJar, $true);" ^
      "                if ((Get-Item $wrapperJar).Length -gt 0) { $downloaded = $true; break }" ^
      "            } else {" ^
      "                $mainEntry = $zip.Entries | Where-Object { $_.FullName -match 'gradle-[^/]+/lib/plugins/gradle-wrapper-main-.*\.jar' } | Select-Object -First 1;" ^
      "                $sharedEntry = $zip.Entries | Where-Object { $_.FullName -match 'gradle-[^/]+/lib/gradle-wrapper-shared-.*\.jar' } | Select-Object -First 1;" ^
      "                if ($mainEntry -and $sharedEntry) {" ^
      "                    $partsDir = New-Item -ItemType Directory -Path (Join-Path $tempDir 'parts') -Force;" ^
      "                    $mainPath = Join-Path $partsDir 'gradle-wrapper-main.jar';" ^
      "                    $sharedPath = Join-Path $partsDir 'gradle-wrapper-shared.jar';" ^
      "                    [IO.Compression.ZipFileExtensions]::ExtractToFile($mainEntry, $mainPath, $true);" ^
      "                    [IO.Compression.ZipFileExtensions]::ExtractToFile($sharedEntry, $sharedPath, $true);" ^
      "                    Merge-JarFiles -OutputPath $wrapperJar -InputPaths @($mainPath, $sharedPath);" ^
      "                    if ((Get-Item $wrapperJar).Length -gt 0) { $downloaded = $true; break }" ^
      "                } else {" ^
      "                    Write-Warning 'Failed to locate gradle-wrapper artifacts in the distribution archive.';" ^
      "                }" ^
      "            }" ^
      "            $zip.Dispose();" ^
      "        } finally {" ^
      "            Remove-Item -Recurse -Force $tempDir -ErrorAction SilentlyContinue;" ^
      "        }" ^
      "    }" ^
      "} catch {" ^
      "    Write-Warning \"Failed to download Gradle wrapper JAR from $url: $($_.Exception.Message)\";" ^
      "} }" ^
      "if (-not $downloaded -or -not (Test-Path $wrapperJar) -or (Get-Item $wrapperJar).Length -eq 0) { throw 'gradle-wrapper.jar is missing and could not be downloaded automatically. Please ensure network connectivity is available.'; }"

    if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%
)

if not exist "%WRAPPER_JAR%" (
    echo ERROR: gradle-wrapper.jar is missing after download attempt.
    exit /b 1
)
for %%A in ("%WRAPPER_JAR%") do if %%~zA equ 0 (
    echo ERROR: gradle-wrapper.jar was downloaded but is empty.
    exit /b 1
)

set CLASSPATH=%WRAPPER_JAR%


@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%GRADLE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
