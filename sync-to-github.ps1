$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path -LiteralPath (Split-Path -Parent $MyInvocation.MyCommand.Path)).Path
$logDirectory = Join-Path $repoRoot '.git-sync'
$logPath = Join-Path $logDirectory 'sync.log'
$gitCandidates = @(
    (Join-Path $env:ProgramFiles 'Git\cmd\git.exe'),
    (Join-Path ${env:ProgramFiles(x86)} 'Git\cmd\git.exe')
)
$gitPath = $gitCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1

if (-not $gitPath) {
    throw 'Git for Windows was not found. Install Git and restart this task.'
}

New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null

function Write-SyncLog {
    param([string]$Message)

    $line = '{0} {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message
    Add-Content -LiteralPath $logPath -Value $line -Encoding UTF8
}

function Invoke-Git {
    param([string[]]$Arguments)

    $output = & $gitPath -C $repoRoot @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        throw ('git {0} failed with exit code {1}: {2}' -f ($Arguments -join ' '), $exitCode, ($output -join ' '))
    }

    return $output
}

function Get-WorkspaceSnapshot {
    $directories = [System.Collections.Generic.Stack[string]]::new()
    $entries = [System.Collections.Generic.List[string]]::new()
    $directories.Push($repoRoot)

    while ($directories.Count -gt 0) {
        $directory = $directories.Pop()
        foreach ($item in Get-ChildItem -LiteralPath $directory -Force -ErrorAction Stop) {
            if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
                continue
            }

            if ($item.PSIsContainer) {
                if ($item.Name -notin @('.git', '.git-sync', 'target')) {
                    $directories.Push($item.FullName)
                }
                continue
            }

            $relativePath = $item.FullName.Substring($repoRoot.Length).TrimStart('\')
            $entries.Add(('{0}|{1}|{2}' -f $relativePath, $item.Length, $item.LastWriteTimeUtc.Ticks))
        }
    }

    $entries.Sort([StringComparer]::OrdinalIgnoreCase)
    return [string]::Join("`n", $entries)
}

function Sync-Workspace {
    $status = Invoke-Git @('status', '--porcelain', '--untracked-files=all')
    if ($status) {
        Invoke-Git @('add', '--all') | Out-Null
        & $gitPath -C $repoRoot diff --cached --quiet
        $diffExitCode = $LASTEXITCODE
        if ($diffExitCode -eq 1) {
            $message = 'Auto-sync: {0}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
            Invoke-Git @('commit', '-m', $message) | Out-Null
        }
        elseif ($diffExitCode -ne 0) {
            throw "git diff --cached --quiet failed with exit code $diffExitCode."
        }
    }

    $pushOutput = Invoke-Git @('push', 'origin', 'main')
    if ($pushOutput) {
        Write-SyncLog ('Push completed: {0}' -f ($pushOutput -join ' '))
    }
    else {
        Write-SyncLog 'Sync check completed; no pending commits to push.'
    }
}

$pathSeparators = [char[]]@('\', '/')
$topLevel = [IO.Path]::GetFullPath((Invoke-Git @('rev-parse', '--show-toplevel') | Select-Object -First 1).Trim()).TrimEnd($pathSeparators)
$normalizedRepoRoot = [IO.Path]::GetFullPath($repoRoot).TrimEnd($pathSeparators)
if (-not [string]::Equals($topLevel, $normalizedRepoRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Git repository root '$topLevel' does not match workspace '$repoRoot'."
}

$branch = (Invoke-Git @('branch', '--show-current') | Select-Object -First 1).Trim()
if ($branch -ne 'main') {
    throw "Expected branch 'main', but the current branch is '$branch'."
}

$remote = (Invoke-Git @('remote', 'get-url', 'origin') | Select-Object -First 1).Trim()
if ($remote -ne 'https://github.com/LarsvanMozart/Minecraft-Plugins.git') {
    throw "Unexpected origin URL '$remote'."
}

$lastSnapshot = Get-WorkspaceSnapshot
$lastChange = [DateTime]::UtcNow
$retryAfter = [DateTime]::MinValue
$syncPending = $true
Write-SyncLog 'Watcher started.'

while ($true) {
    Start-Sleep -Seconds 2

    try {
        $currentSnapshot = Get-WorkspaceSnapshot
    }
    catch {
        Write-SyncLog ('ERROR reading workspace: {0}' -f $_.Exception.Message)
        Start-Sleep -Seconds 10
        continue
    }

    if ($currentSnapshot -ne $lastSnapshot) {
        $lastSnapshot = $currentSnapshot
        $lastChange = [DateTime]::UtcNow
        $syncPending = $true
    }

    if ($syncPending -and ([DateTime]::UtcNow - $lastChange).TotalSeconds -ge 5 -and [DateTime]::UtcNow -ge $retryAfter) {
        try {
            Sync-Workspace
            $afterSyncSnapshot = Get-WorkspaceSnapshot
            if ($afterSyncSnapshot -ne $lastSnapshot) {
                $lastSnapshot = $afterSyncSnapshot
                $lastChange = [DateTime]::UtcNow
                $syncPending = $true
            }
            else {
                $syncPending = $false
            }
            $retryAfter = [DateTime]::MinValue
        }
        catch {
            Write-SyncLog ('ERROR syncing workspace: {0}' -f $_.Exception.Message)
            $retryAfter = [DateTime]::UtcNow.AddSeconds(30)
        }
    }
}
