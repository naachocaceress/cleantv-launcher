<#
.SYNOPSIS
  Bring Gathin23/cleanyourtv into this fork, drop the upstream-only files this fork does not use, and check the build.

.EXAMPLE
  tools\sync-upstream.ps1            # merge upstream/main, clean up, run the tests and lint
  tools\sync-upstream.ps1 -NoBuild   # merge and clean up only

  Run it from a clean branch. If the merge stops on a conflict, resolve it, commit, and run the script again (it then only
  cleans up and builds). It never pushes.
#>
param([switch]$NoBuild)
$ErrorActionPreference = 'Continue'   # git writes progress to stderr; failures are checked by exit code
function G { & git @args 2>&1 | ForEach-Object { "$_" } }
Set-Location (Split-Path -Parent $PSScriptRoot)

if (git status --porcelain) { throw 'The working tree is not clean: commit or stash first.' }
if (-not (git remote | Select-String -SimpleMatch 'upstream')) {
    G remote add upstream https://github.com/Gathin23/cleanyourtv.git
}
G fetch upstream
if ($LASTEXITCODE) { throw 'git fetch upstream failed.' }

$ahead = [int](git rev-list --count HEAD..upstream/main)
if ($ahead -eq 0) { Write-Host 'Already up to date with upstream/main.' }
else {
    Write-Host "Merging $ahead upstream commits..."
    G merge upstream/main --no-edit
    if ($LASTEXITCODE) { throw 'The merge stopped on a conflict: resolve it, commit, and run this script again.' }
}

# Upstream's CI (with an APK size cap), its maintenance bot, funding link, issue templates and the rules it writes for
# coding agents are not for this fork; our own CI is .github/workflows/cleantv-ci.yml and stays.
$drop = @('.github/workflows/ci.yml', '.github/workflows/addons.yml', '.github/workflows/maintainer-bot.yml',
          '.github/FUNDING.yml', '.github/maintainer', '.github/ISSUE_TEMPLATE', '.github/pull_request_template.md',
          'CLAUDE.md', 'AGENTS.md', 'GEMINI.md')
$present = $drop | Where-Object { Test-Path $_ }
if ($present) {
    G rm -r -q -f @present
    G commit -q -m 'Drop upstream-only files this fork does not use (CI, maintainer bot, agent rules)'
    Write-Host ('Removed: ' + ($present -join ', '))
}

if (-not $NoBuild) {
    Push-Location launcher
    try {
        if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
        & .\gradlew.bat testDebugUnitTest lintRelease assembleRelease --console=plain
        if ($LASTEXITCODE) { throw 'The build, tests or lint failed after the merge.' }
    } finally { Pop-Location }
}
Write-Host 'Done. Review with git log, then push when you are happy.'
