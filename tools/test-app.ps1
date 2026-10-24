$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$jdkDirectory = Join-Path $projectRoot '.tools\jdk\jdk-17.0.20.1+1'
if ($env:JAVA_HOME) { $jdkDirectory = $env:JAVA_HOME }
$classes = Join-Path $projectRoot '.tools\app-tests'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
Push-Location $projectRoot
try {
    $sourceRoot = 'app\src\main\java\io\github\sakurainakeipoi\hapticboost'
    & (Join-Path $jdkDirectory 'bin\javac.exe') -encoding UTF-8 -d $classes "$sourceRoot\ModuleProtocol.java" "$sourceRoot\ShellRunner.java" "$sourceRoot\PreviewPattern.java" 'tools\tests\AppProtocolTest.java'
    if ($LASTEXITCODE -ne 0) { throw 'App protocol test compilation failed' }
    & (Join-Path $jdkDirectory 'bin\java.exe') -cp $classes io.github.sakurainakeipoi.hapticboost.AppProtocolTest
    if ($LASTEXITCODE -ne 0) { throw 'App protocol tests failed' }
} finally {
    Pop-Location
}
