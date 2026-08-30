[CmdletBinding()]
param(
    [Parameter()]
    [string]$FfmpegPath = "ffmpeg",

    [Parameter()]
    [string]$FfprobePath = "ffprobe",

    [ValidateRange(1, 16)]
    [int]$DownloadThrottle = 6,

    [switch]$ForceDownload,

    [switch]$Offline,

    [Parameter()]
    [string]$SelectedSourceRoot = "",

    [Parameter()]
    [string]$SelectedManifestPath = "",

    [switch]$SkipSelected
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$LoopSeconds = 60.0
$SeamSeconds = 3.0
$PreparedSeconds = $LoopSeconds + $SeamSeconds
$SampleRate = 48000
$TargetLoudnessLufs = -22.0
$TargetLra = 7.0
$TargetTruePeakDb = -2.0

$RepoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$ManifestPath = Join-Path $PSScriptRoot "focus_audio_sources.tsv"
$SourceRoot = Join-Path $RepoRoot "app/build/focus-audio-sources"
$DownloadRoot = Join-Path $SourceRoot "downloads"
$WorkRoot = Join-Path $SourceRoot "work"
$BedRoot = Join-Path $WorkRoot "beds"
$MixRoot = Join-Path $WorkRoot "mixes"
$OutputRoot = Join-Path $RepoRoot "app/src/main/assets/focus/ambience"
$HashManifestPath = Join-Path $SourceRoot "SOURCE_HASHES.tsv"
$SelectedHashManifestPath = Join-Path $SourceRoot "SELECTED_SOURCE_HASHES.tsv"
if ([string]::IsNullOrWhiteSpace($SelectedManifestPath)) {
    $SelectedManifestPath = Join-Path $PSScriptRoot "focus_selected_audio_sources.tsv"
}

function Format-InvariantNumber {
    param([Parameter(Mandatory)][double]$Value)

    return $Value.ToString("0.######", [System.Globalization.CultureInfo]::InvariantCulture)
}

function Resolve-Executable {
    param(
        [Parameter(Mandatory)][string]$Value,
        [Parameter(Mandatory)][string]$FriendlyName
    )

    if (Test-Path -LiteralPath $Value -PathType Leaf) {
        return (Resolve-Path -LiteralPath $Value).Path
    }

    $command = Get-Command -Name $Value -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -eq $command) {
        throw "$FriendlyName was not found: $Value"
    }
    return $command.Source
}

function Invoke-NativeCapture {
    param(
        [Parameter(Mandatory)][string]$Executable,
        [Parameter(Mandatory)][string[]]$Arguments,
        [Parameter(Mandatory)][string]$Description
    )

    $output = @(& $Executable @Arguments 2>&1 | ForEach-Object { "$_" })
    if ($LASTEXITCODE -ne 0) {
        throw "$Description failed with exit code $LASTEXITCODE.`n$($output -join "`n")"
    }
    return ,$output
}

function Invoke-Native {
    param(
        [Parameter(Mandatory)][string]$Executable,
        [Parameter(Mandatory)][string[]]$Arguments,
        [Parameter(Mandatory)][string]$Description
    )

    $output = Invoke-NativeCapture -Executable $Executable -Arguments $Arguments -Description $Description
    foreach ($line in $output) {
        Write-Verbose $line
    }
}

function Get-AudioDurationSeconds {
    param([Parameter(Mandatory)][string]$Path)

    $lines = Invoke-NativeCapture -Executable $script:FfprobeExe -Description "ffprobe $Path" -Arguments @(
        "-v", "error",
        "-show_entries", "format=duration",
        "-of", "default=noprint_wrappers=1:nokey=1",
        $Path
    )
    $raw = ($lines | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Last 1).Trim()
    $duration = 0.0
    if (-not [double]::TryParse(
        $raw,
        [System.Globalization.NumberStyles]::Float,
        [System.Globalization.CultureInfo]::InvariantCulture,
        [ref]$duration
    )) {
        throw "ffprobe returned an invalid duration for ${Path}: $raw"
    }
    if ($duration -le 0.25) {
        throw "Audio source is too short: $Path ($duration seconds)"
    }
    return $duration
}

function Get-DownloadExtension {
    param([Parameter(Mandatory)][string]$Url)

    $extension = [System.IO.Path]::GetExtension(([Uri]$Url).AbsolutePath).ToLowerInvariant()
    if ($extension -notin @(".wav", ".mp3", ".ogg", ".flac", ".m4a")) {
        return ".audio"
    }
    return $extension
}

function Get-DownloadPath {
    param([Parameter(Mandatory)]$Row)

    return Join-Path $DownloadRoot ($Row.source_key + (Get-DownloadExtension -Url $Row.direct_url))
}

function Assert-Manifest {
    param([Parameter(Mandatory)][object[]]$Rows)

    if ($Rows.Count -eq 0) {
        throw "Audio source manifest is empty: $ManifestPath"
    }

    $requiredColumns = @(
        "source_key", "output_id", "category", "author", "title",
        "landing_url", "direct_url", "license", "flags"
    )
    foreach ($column in $requiredColumns) {
        if ($column -notin $Rows[0].PSObject.Properties.Name) {
            throw "Missing manifest column: $column"
        }
    }

    $sourceKeys = @{}
    foreach ($row in $Rows) {
        if ([string]::IsNullOrWhiteSpace($row.source_key) -or
            $row.source_key -notmatch "^[a-z0-9_]+$") {
            throw "Invalid source_key: '$($row.source_key)'"
        }
        if ($sourceKeys.ContainsKey($row.source_key)) {
            throw "Duplicate source_key: $($row.source_key)"
        }
        $sourceKeys[$row.source_key] = $true

        if ([string]::IsNullOrWhiteSpace($row.direct_url) -or
            -not $row.direct_url.StartsWith("https://", [StringComparison]::OrdinalIgnoreCase)) {
            throw "Only HTTPS sources are accepted: $($row.source_key)"
        }
        if ($row.license -ne "CC0-1.0") {
            throw "Only CC0-1.0 sources are accepted by this pipeline: $($row.source_key)"
        }
        if ($row.category -notin @("study", "spaces", "weather", "nature", "cozy", "travel")) {
            throw "Invalid category for $($row.source_key): $($row.category)"
        }
        if (-not [string]::IsNullOrWhiteSpace($row.output_id) -and
            $row.output_id -notmatch "^[a-z0-9_]+$") {
            throw "Invalid output_id: '$($row.output_id)'"
        }
    }

    foreach ($requiredSource in @(
        "quiet_reading_room", "turning_pages", "rainy_cafe_cafe",
        "summer_rain", "rainy_night_city"
    )) {
        if (-not $sourceKeys.ContainsKey($requiredSource)) {
            throw "Special mix source is missing from the manifest: $requiredSource"
        }
    }
}

function Assert-SelectedManifest {
    param([Parameter(Mandatory)][object[]]$Rows)

    if ($Rows.Count -eq 0) {
        throw "Selected audio manifest is empty: $SelectedManifestPath"
    }
    $requiredColumns = @(
        "file_name", "output_id", "category", "title_ru", "title_en",
        "author", "landing_url", "license", "sha256"
    )
    foreach ($column in $requiredColumns) {
        if ($column -notin $Rows[0].PSObject.Properties.Name) {
            throw "Missing selected manifest column: $column"
        }
    }

    $outputIds = @{}
    $fileNames = @{}
    foreach ($row in $Rows) {
        if ([string]::IsNullOrWhiteSpace($row.file_name) -or
            [System.IO.Path]::GetFileName($row.file_name) -ne $row.file_name) {
            throw "Selected source must be a plain file name: '$($row.file_name)'"
        }
        if ($fileNames.ContainsKey($row.file_name)) {
            throw "Duplicate selected source file: $($row.file_name)"
        }
        $fileNames[$row.file_name] = $true
        if ($row.output_id -notmatch "^[a-z0-9_]+$") {
            throw "Invalid selected output_id: '$($row.output_id)'"
        }
        if ($outputIds.ContainsKey($row.output_id)) {
            throw "Duplicate selected output_id: $($row.output_id)"
        }
        $outputIds[$row.output_id] = $true
        if ($row.category -notin @("study", "spaces", "weather", "nature", "cozy", "travel", "melody")) {
            throw "Invalid selected category for $($row.output_id): $($row.category)"
        }
        if ($row.license -ne "Pixabay-Content-License") {
            throw "Unexpected selected source licence for $($row.output_id): $($row.license)"
        }
        if ($row.sha256 -notmatch "^[a-fA-F0-9]{64}$") {
            throw "Invalid selected source SHA-256 for $($row.output_id)"
        }
        if (-not $row.landing_url.StartsWith("https://pixabay.com/", [StringComparison]::OrdinalIgnoreCase)) {
            throw "Selected source page must use HTTPS on pixabay.com: $($row.output_id)"
        }
    }
}

function Get-ExpectedOutputs {
    return [ordered]@{
        study = @(
            "large_library", "quiet_reading_room", "pencil_on_paper", "fountain_pen",
            "turning_pages", "soft_keyboard", "mechanical_keyboard", "distant_lecture_hall"
        )
        spaces = @(
            "morning_cafe", "rainy_cafe", "evening_office", "bookshop",
            "university_archive", "museum_hall"
        )
        weather = @(
            "rain_on_window", "rain_on_tent", "distant_thunder", "snowstorm",
            "summer_rain", "rainy_night_city"
        )
        nature = @(
            "forest_stream", "ocean_waves", "quiet_lake", "wind_in_pines",
            "night_crickets", "campfire"
        )
        cozy = @(
            "fireplace", "cat_purring", "aquarium", "ceiling_fan",
            "vinyl_crackle", "next_room"
        )
        travel = @(
            "night_train", "airplane_cabin", "car_in_rain", "ferry_cabin",
            "city_tram", "orbital_station"
        )
    }
}

function Assert-OutputCoverage {
    param([Parameter(Mandatory)][object[]]$Rows)

    $specialIds = @("rainy_cafe", "university_archive", "turning_pages", "rainy_night_city")
    $declared = @(
        $Rows |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_.output_id) } |
            ForEach-Object { $_.output_id }
    ) + $specialIds

    $duplicates = @($declared | Group-Object | Where-Object { $_.Count -gt 1 -and $_.Name -notin $specialIds })
    if ($duplicates.Count -gt 0) {
        throw "Duplicate output IDs: $($duplicates.Name -join ', ')"
    }

    $expected = Get-ExpectedOutputs
    $expectedIds = @($expected.Values | ForEach-Object { $_ })
    $missing = @($expectedIds | Where-Object { $_ -notin $declared })
    $unexpected = @($declared | Sort-Object -Unique | Where-Object { $_ -notin $expectedIds })
    if ($missing.Count -gt 0 -or $unexpected.Count -gt 0) {
        throw "Output coverage mismatch. Missing: $($missing -join ', '); unexpected: $($unexpected -join ', ')"
    }
}

function Start-ParallelDownloads {
    param([Parameter(Mandatory)][object[]]$Rows)

    $queue = [System.Collections.Queue]::new()
    foreach ($row in $Rows) {
        $destination = Get-DownloadPath -Row $row
        $cached = Test-Path -LiteralPath $destination -PathType Leaf
        if ($cached -and (Get-Item -LiteralPath $destination).Length -gt 1024 -and -not $ForceDownload) {
            Write-Host "Using cached source: $($row.source_key)"
            continue
        }
        if ($Offline) {
            throw "Offline mode: missing cached source $($row.source_key) at $destination"
        }
        $queue.Enqueue([pscustomobject]@{
            SourceKey = $row.source_key
            Url = $row.direct_url
            Destination = $destination
        })
    }

    $active = @()
    try {
        while ($queue.Count -gt 0 -or $active.Count -gt 0) {
            while ($queue.Count -gt 0 -and $active.Count -lt $DownloadThrottle) {
                $item = $queue.Dequeue()
                $job = Start-Job -ArgumentList @($item.SourceKey, $item.Url, $item.Destination) -ScriptBlock {
                    param($SourceKey, $Url, $Destination)

                    $ErrorActionPreference = "Stop"
                    $partPath = "$Destination.part"
                    try {
                        if (Test-Path -LiteralPath $partPath) {
                            Remove-Item -LiteralPath $partPath -Force
                        }
                        $request = @{
                            Uri = $Url
                            OutFile = $partPath
                            Headers = @{ "User-Agent" = "MIRL-focus-audio-preparer/1.0" }
                            MaximumRedirection = 10
                        }
                        if ($PSVersionTable.PSVersion.Major -lt 6) {
                            $request.UseBasicParsing = $true
                        }
                        Invoke-WebRequest @request | Out-Null
                        if (-not (Test-Path -LiteralPath $partPath -PathType Leaf) -or
                            (Get-Item -LiteralPath $partPath).Length -le 1024) {
                            throw "Downloaded file is empty or unexpectedly small"
                        }
                        Move-Item -LiteralPath $partPath -Destination $Destination -Force
                        [pscustomobject]@{
                            SourceKey = $SourceKey
                            Bytes = (Get-Item -LiteralPath $Destination).Length
                        }
                    } finally {
                        if (Test-Path -LiteralPath $partPath) {
                            Remove-Item -LiteralPath $partPath -Force
                        }
                    }
                }
                $job | Add-Member -NotePropertyName MirlSourceKey -NotePropertyValue $item.SourceKey
                $active += $job
                Write-Host "Downloading $($item.SourceKey)..."
            }

            if ($active.Count -eq 0) {
                continue
            }

            $completed = Wait-Job -Job $active -Any
            $jobOutput = @(Receive-Job -Job $completed -ErrorAction SilentlyContinue)
            if ($completed.State -ne "Completed") {
                $reason = $completed.ChildJobs[0].JobStateInfo.Reason
                throw "Download failed for $($completed.MirlSourceKey): $reason"
            }
            foreach ($result in $jobOutput) {
                if ($null -ne $result.SourceKey) {
                    Write-Host "Downloaded $($result.SourceKey): $($result.Bytes) bytes"
                }
            }
            Remove-Job -Job $completed -Force
            $active = @($active | Where-Object { $_.Id -ne $completed.Id })
        }
    } finally {
        foreach ($job in $active) {
            Stop-Job -Job $job -ErrorAction SilentlyContinue
            Remove-Job -Job $job -Force -ErrorAction SilentlyContinue
        }
    }
}

function Write-SourceHashManifest {
    param([Parameter(Mandatory)][object[]]$Rows)

    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add("source_key`tsha256`tbytes`tdirect_url")
    foreach ($row in ($Rows | Sort-Object source_key)) {
        $path = Get-DownloadPath -Row $row
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw "Downloaded source is missing: $path"
        }
        $file = Get-Item -LiteralPath $path
        $hash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
        $lines.Add("$($row.source_key)`t$hash`t$($file.Length)`t$($row.direct_url)")
    }
    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllLines($HashManifestPath, $lines, $utf8NoBom)
}

function New-ContinuousBed {
    param(
        [Parameter(Mandatory)][string]$InputPath,
        [Parameter(Mandatory)][string]$OutputPath,
        [Parameter(Mandatory)][string]$SourceKey
    )

    $duration = Get-AudioDurationSeconds -Path $InputPath
    $target = Format-InvariantNumber $PreparedSeconds
    $baseFilter = "aresample=$SampleRate,aformat=sample_fmts=fltp:sample_rates=${SampleRate}:channel_layouts=stereo"
    $levelFilter = "loudnorm=I=-24:LRA=9:TP=-3,alimiter=limit=0.86"

    if ($duration -ge $PreparedSeconds) {
        $start = [Math]::Max(0.0, ($duration - $PreparedSeconds) / 2.0)
        $end = $start + $PreparedSeconds
        $filter = "[0:a]$baseFilter,atrim=start=$(Format-InvariantNumber $start):end=$(Format-InvariantNumber $end),asetpts=PTS-STARTPTS,$levelFilter[out]"
    } else {
        $crossfade = [Math]::Min(1.5, [Math]::Max(0.15, $duration * 0.12))
        if (($duration - $crossfade) -le 0.05) {
            throw "Source is too short to extend safely: $SourceKey ($duration seconds)"
        }
        $repeatCount = [int][Math]::Ceiling(($PreparedSeconds - $crossfade) / ($duration - $crossfade))
        if ($repeatCount -gt 96) {
            throw "Source would require too many repetitions: $SourceKey ($repeatCount)"
        }

        $labels = (0..($repeatCount - 1) | ForEach-Object { "[segment$_]" }) -join ""
        $parts = @(
            "[0:a]$baseFilter,atrim=start=0:end=$(Format-InvariantNumber $duration),asetpts=PTS-STARTPTS,asplit=$repeatCount$labels"
        )
        $current = "[segment0]"
        for ($index = 1; $index -lt $repeatCount; $index++) {
            $next = "[joined$index]"
            $parts += "${current}[segment$index]acrossfade=d=$(Format-InvariantNumber $crossfade):c1=tri:c2=tri$next"
            $current = $next
        }
        $parts += "${current}atrim=start=0:end=$target,asetpts=PTS-STARTPTS,$levelFilter[out]"
        $filter = $parts -join ";"
    }

    Write-Host "Preparing real source bed: $SourceKey"
    Invoke-Native -Executable $script:FfmpegExe -Description "prepare bed $SourceKey" -Arguments @(
        "-hide_banner", "-nostdin", "-y",
        "-i", $InputPath,
        "-filter_complex", $filter,
        "-map", "[out]",
        "-t", $target,
        "-ar", "$SampleRate", "-ac", "2",
        "-c:a", "pcm_s16le",
        $OutputPath
    )
}

function New-PageEventSource {
    param(
        [Parameter(Mandatory)][string]$InputPath,
        [Parameter(Mandatory)][string]$OutputPath
    )

    $duration = Get-AudioDurationSeconds -Path $InputPath
    $clipDuration = [Math]::Min(30.0, $duration)
    $start = [Math]::Max(0.0, ($duration - $clipDuration) / 2.0)
    $end = $start + $clipDuration
    $filter = "[0:a]aresample=$SampleRate,aformat=sample_fmts=fltp:sample_rates=${SampleRate}:channel_layouts=stereo," +
        "atrim=start=$(Format-InvariantNumber $start):end=$(Format-InvariantNumber $end),asetpts=PTS-STARTPTS," +
        "loudnorm=I=-19:LRA=8:TP=-2.5,alimiter=limit=0.9[out]"

    Invoke-Native -Executable $script:FfmpegExe -Description "prepare page events" -Arguments @(
        "-hide_banner", "-nostdin", "-y",
        "-i", $InputPath,
        "-filter_complex", $filter,
        "-map", "[out]",
        "-ar", "$SampleRate", "-ac", "2",
        "-c:a", "pcm_s16le",
        $OutputPath
    )
}

function New-TwoSourceMix {
    param(
        [Parameter(Mandatory)][string]$FirstPath,
        [Parameter(Mandatory)][double]$FirstGain,
        [Parameter(Mandatory)][string]$SecondPath,
        [Parameter(Mandatory)][double]$SecondGain,
        [Parameter(Mandatory)][string]$OutputPath,
        [Parameter(Mandatory)][string]$MixName
    )

    $filter = "[0:a]volume=$(Format-InvariantNumber $FirstGain)[first];" +
        "[1:a]volume=$(Format-InvariantNumber $SecondGain)[second];" +
        "[first][second]amix=inputs=2:duration=first:dropout_transition=0:normalize=0," +
        "alimiter=limit=0.95[out]"
    Invoke-Native -Executable $script:FfmpegExe -Description "mix $MixName" -Arguments @(
        "-hide_banner", "-nostdin", "-y",
        "-i", $FirstPath,
        "-i", $SecondPath,
        "-filter_complex", $filter,
        "-map", "[out]",
        "-t", (Format-InvariantNumber $PreparedSeconds),
        "-ar", "$SampleRate", "-ac", "2",
        "-c:a", "pcm_s16le",
        $OutputPath
    )
}

function New-PageLayeredMix {
    param(
        [Parameter(Mandatory)][string]$BedPath,
        [Parameter(Mandatory)][string]$PageEventPath,
        [Parameter(Mandatory)][int[]]$EventTimesSeconds,
        [Parameter(Mandatory)][double]$BedGain,
        [Parameter(Mandatory)][double]$EventGain,
        [Parameter(Mandatory)][string]$OutputPath,
        [Parameter(Mandatory)][string]$MixName
    )

    $eventDurationAvailable = Get-AudioDurationSeconds -Path $PageEventPath
    $eventDuration = [Math]::Min(3.2, $eventDurationAvailable)
    $maxOffset = [Math]::Max(0.0, $eventDurationAvailable - $eventDuration - 0.02)
    $splitLabels = (0..($EventTimesSeconds.Count - 1) | ForEach-Object { "[page$_]" }) -join ""
    $parts = @(
        "[0:a]volume=$(Format-InvariantNumber $BedGain)[bed]",
        "[1:a]asplit=$($EventTimesSeconds.Count)$splitLabels"
    )
    $mixLabels = @("[bed]")
    for ($index = 0; $index -lt $EventTimesSeconds.Count; $index++) {
        $sourceOffset = if ($maxOffset -gt 0.0) {
            ($index * 5.173) % $maxOffset
        } else {
            0.0
        }
        $sourceEnd = $sourceOffset + $eventDuration
        $fadeOutStart = [Math]::Max(0.0, $eventDuration - 0.18)
        $delayMs = $EventTimesSeconds[$index] * 1000
        $label = "[event$index]"
        $parts += "[page$index]atrim=start=$(Format-InvariantNumber $sourceOffset):end=$(Format-InvariantNumber $sourceEnd)," +
            "asetpts=PTS-STARTPTS,afade=t=in:st=0:d=0.03," +
            "afade=t=out:st=$(Format-InvariantNumber $fadeOutStart):d=0.18," +
            "volume=$(Format-InvariantNumber $EventGain),adelay=$delayMs|$delayMs$label"
        $mixLabels += $label
    }
    $parts += ($mixLabels -join "") +
        "amix=inputs=$($mixLabels.Count):duration=first:dropout_transition=0:normalize=0," +
        "alimiter=limit=0.95[out]"

    Invoke-Native -Executable $script:FfmpegExe -Description "mix $MixName" -Arguments @(
        "-hide_banner", "-nostdin", "-y",
        "-i", $BedPath,
        "-i", $PageEventPath,
        "-filter_complex", ($parts -join ";"),
        "-map", "[out]",
        "-t", (Format-InvariantNumber $PreparedSeconds),
        "-ar", "$SampleRate", "-ac", "2",
        "-c:a", "pcm_s16le",
        $OutputPath
    )
}

function Get-SeamFilterPrefix {
    $loop = Format-InvariantNumber $LoopSeconds
    $seam = Format-InvariantNumber $SeamSeconds
    $prepared = Format-InvariantNumber $PreparedSeconds
    return "[0:a]asplit=3[body_source][tail_source][head_source];" +
        "[body_source]atrim=start=${seam}:end=${loop},asetpts=PTS-STARTPTS[body];" +
        "[tail_source]atrim=start=${loop}:end=${prepared},asetpts=PTS-STARTPTS[tail];" +
        "[head_source]atrim=start=0:end=${seam},asetpts=PTS-STARTPTS[head];" +
        "[tail][head]acrossfade=d=${seam}:c1=tri:c2=tri[seam];" +
        "[body][seam]concat=n=2:v=0:a=1[loop]"
}

function Export-SeamlessOgg {
    param(
        [Parameter(Mandatory)][string]$InputPath,
        [Parameter(Mandatory)][string]$OutputPath,
        [Parameter(Mandatory)][string]$OutputId,
        [Parameter()][string]$ProvenanceComment = "CC0 source provenance: assets/focus/AUDIO_SOURCES.md"
    )

    $seamPrefix = Get-SeamFilterPrefix
    $analysisFilter = "$seamPrefix;[loop]loudnorm=I=$(Format-InvariantNumber $TargetLoudnessLufs):" +
        "LRA=$(Format-InvariantNumber $TargetLra):TP=$(Format-InvariantNumber $TargetTruePeakDb):" +
        "print_format=json[normalized]"
    $analysisLines = Invoke-NativeCapture -Executable $script:FfmpegExe -Description "measure loudness $OutputId" -Arguments @(
        "-hide_banner", "-nostdin", "-nostats",
        "-i", $InputPath,
        "-filter_complex", $analysisFilter,
        "-map", "[normalized]",
        "-f", "null", "-"
    )
    $analysisText = $analysisLines -join "`n"
    $jsonMatch = [regex]::Match($analysisText, "\{\s*`"input_i`"[\s\S]*?\}")
    if (-not $jsonMatch.Success) {
        throw "Could not parse loudnorm analysis for $OutputId.`n$analysisText"
    }
    $measurement = $jsonMatch.Value | ConvertFrom-Json
    foreach ($property in @("input_i", "input_tp", "input_lra", "input_thresh", "target_offset")) {
        if ($measurement.$property -eq "-inf" -or [string]::IsNullOrWhiteSpace("$($measurement.$property)")) {
            throw "Invalid loudness measurement for ${OutputId}: $property=$($measurement.$property)"
        }
    }

    $normalizer = "loudnorm=I=$(Format-InvariantNumber $TargetLoudnessLufs):" +
        "LRA=$(Format-InvariantNumber $TargetLra):TP=$(Format-InvariantNumber $TargetTruePeakDb):" +
        "measured_I=$($measurement.input_i):measured_TP=$($measurement.input_tp):" +
        "measured_LRA=$($measurement.input_lra):measured_thresh=$($measurement.input_thresh):" +
        "offset=$($measurement.target_offset):linear=true:print_format=summary"
    $encodeFilter = "$seamPrefix;[loop]$normalizer[normalized]"
    $temporaryOutput = "$OutputPath.build.ogg"
    if (Test-Path -LiteralPath $temporaryOutput) {
        Remove-Item -LiteralPath $temporaryOutput -Force
    }

    Write-Host "Encoding real 60-second loop: $OutputId"
    try {
        Invoke-Native -Executable $script:FfmpegExe -Description "encode $OutputId" -Arguments @(
            "-hide_banner", "-nostdin", "-y",
            "-i", $InputPath,
            "-filter_complex", $encodeFilter,
            "-map", "[normalized]",
            "-t", (Format-InvariantNumber $LoopSeconds),
            "-ar", "$SampleRate", "-ac", "2",
            "-c:a", "libvorbis", "-q:a", "4",
            "-metadata", "title=$OutputId",
            "-metadata", "comment=$ProvenanceComment",
            $temporaryOutput
        )
        $duration = Get-AudioDurationSeconds -Path $temporaryOutput
        if ([Math]::Abs($duration - $LoopSeconds) -gt 0.25) {
            throw "Encoded duration is not 60 seconds for ${OutputId}: $duration"
        }
        Move-Item -LiteralPath $temporaryOutput -Destination $OutputPath -Force
    } finally {
        if (Test-Path -LiteralPath $temporaryOutput) {
            Remove-Item -LiteralPath $temporaryOutput -Force
        }
    }
}

if (-not (Test-Path -LiteralPath $ManifestPath -PathType Leaf)) {
    throw "Source manifest not found: $ManifestPath"
}
if ($SkipSelected -and -not [string]::IsNullOrWhiteSpace($SelectedSourceRoot)) {
    throw "Use either -SelectedSourceRoot or -SkipSelected, not both."
}
if (-not $SkipSelected -and
    [string]::IsNullOrWhiteSpace($SelectedSourceRoot) -and
    (Test-Path -LiteralPath $SelectedManifestPath -PathType Leaf)) {
    throw "The approved-source manifest exists. Supply -SelectedSourceRoot, or explicitly use -SkipSelected for a base-only build."
}

$script:FfmpegExe = Resolve-Executable -Value $FfmpegPath -FriendlyName "FFmpeg"
$script:FfprobeExe = Resolve-Executable -Value $FfprobePath -FriendlyName "FFprobe"
$rows = @(Import-Csv -LiteralPath $ManifestPath -Delimiter "`t" -Encoding UTF8)
Assert-Manifest -Rows $rows
Assert-OutputCoverage -Rows $rows

foreach ($directory in @($SourceRoot, $DownloadRoot, $WorkRoot, $BedRoot, $MixRoot, $OutputRoot)) {
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
}

Start-ParallelDownloads -Rows $rows
Write-SourceHashManifest -Rows $rows

$rowBySource = @{}
foreach ($row in $rows) {
    $rowBySource[$row.source_key] = $row
}

$specialOutputIds = @("rainy_cafe", "university_archive", "turning_pages", "rainy_night_city")
$bedBySource = @{}
foreach ($row in $rows) {
    if ($row.source_key -eq "turning_pages") {
        continue
    }
    $bedPath = Join-Path $BedRoot ($row.source_key + ".wav")
    $bedParameters = @{
        InputPath = Get-DownloadPath -Row $row
        OutputPath = $bedPath
        SourceKey = $row.source_key
    }
    New-ContinuousBed @bedParameters
    $bedBySource[$row.source_key] = $bedPath
}

$pageEventPath = Join-Path $WorkRoot "turning_pages_events.wav"
$pageEventParameters = @{
    InputPath = Get-DownloadPath -Row $rowBySource["turning_pages"]
    OutputPath = $pageEventPath
}
New-PageEventSource @pageEventParameters

foreach ($row in $rows) {
    if ([string]::IsNullOrWhiteSpace($row.output_id) -or $row.output_id -in $specialOutputIds) {
        continue
    }
    $categoryDirectory = Join-Path $OutputRoot $row.category
    New-Item -ItemType Directory -Path $categoryDirectory -Force | Out-Null
    $exportParameters = @{
        InputPath = $bedBySource[$row.source_key]
        OutputPath = Join-Path $categoryDirectory ($row.output_id + ".ogg")
        OutputId = $row.output_id
    }
    Export-SeamlessOgg @exportParameters
}

$rainyCafeMix = Join-Path $MixRoot "rainy_cafe.wav"
$rainyCafeParameters = @{
    FirstPath = $bedBySource["rainy_cafe_cafe"]
    FirstGain = 0.67
    SecondPath = $bedBySource["summer_rain"]
    SecondGain = 0.53
    OutputPath = $rainyCafeMix
    MixName = "rainy_cafe"
}
New-TwoSourceMix @rainyCafeParameters

$rainyNightCityMix = Join-Path $MixRoot "rainy_night_city.wav"
$rainyNightCityParameters = @{
    FirstPath = $bedBySource["rainy_night_city"]
    FirstGain = 0.70
    SecondPath = $bedBySource["summer_rain"]
    SecondGain = 0.48
    OutputPath = $rainyNightCityMix
    MixName = "rainy_night_city"
}
New-TwoSourceMix @rainyNightCityParameters

$turningPagesMix = Join-Path $MixRoot "turning_pages.wav"
$turningPagesParameters = @{
    BedPath = $bedBySource["quiet_reading_room"]
    PageEventPath = $pageEventPath
    EventTimesSeconds = @(8, 19, 31, 44, 56)
    BedGain = 0.72
    EventGain = 0.95
    OutputPath = $turningPagesMix
    MixName = "turning_pages"
}
New-PageLayeredMix @turningPagesParameters

$universityArchiveMix = Join-Path $MixRoot "university_archive.wav"
$universityArchiveParameters = @{
    BedPath = $bedBySource["quiet_reading_room"]
    PageEventPath = $pageEventPath
    EventTimesSeconds = @(17, 39, 55)
    BedGain = 0.88
    EventGain = 0.62
    OutputPath = $universityArchiveMix
    MixName = "university_archive"
}
New-PageLayeredMix @universityArchiveParameters

$specialMixes = @(
    [pscustomobject]@{ Id = "rainy_cafe"; Category = "spaces"; Path = $rainyCafeMix },
    [pscustomobject]@{ Id = "university_archive"; Category = "spaces"; Path = $universityArchiveMix },
    [pscustomobject]@{ Id = "turning_pages"; Category = "study"; Path = $turningPagesMix },
    [pscustomobject]@{ Id = "rainy_night_city"; Category = "weather"; Path = $rainyNightCityMix }
)
foreach ($mix in $specialMixes) {
    $categoryDirectory = Join-Path $OutputRoot $mix.Category
    New-Item -ItemType Directory -Path $categoryDirectory -Force | Out-Null
    $exportParameters = @{
        InputPath = $mix.Path
        OutputPath = Join-Path $categoryDirectory ($mix.Id + ".ogg")
        OutputId = $mix.Id
    }
    Export-SeamlessOgg @exportParameters
}

$expectedOutputs = Get-ExpectedOutputs
$verifiedCount = 0
foreach ($category in $expectedOutputs.Keys) {
    foreach ($id in $expectedOutputs[$category]) {
        $path = Join-Path (Join-Path $OutputRoot $category) ($id + ".ogg")
        if (-not (Test-Path -LiteralPath $path -PathType Leaf) -or
            (Get-Item -LiteralPath $path).Length -le 1024) {
            throw "Expected output is missing or empty: $path"
        }
        $duration = Get-AudioDurationSeconds -Path $path
        if ([Math]::Abs($duration - $LoopSeconds) -gt 0.25) {
            throw "Unexpected output duration for ${id}: $duration"
        }
        $verifiedCount++
    }
}

if ($verifiedCount -ne 38) {
    throw "Expected 38 ambience loops, verified $verifiedCount"
}

Write-Host "Prepared and verified $verifiedCount real-recording focus loops."
Write-Host "Outputs: $OutputRoot"
Write-Host "Downloaded source hashes: $HashManifestPath"

if (-not [string]::IsNullOrWhiteSpace($SelectedSourceRoot)) {
    if (-not (Test-Path -LiteralPath $SelectedManifestPath -PathType Leaf)) {
        throw "Selected source manifest not found: $SelectedManifestPath"
    }
    $selectedRoot = [System.IO.Path]::GetFullPath($SelectedSourceRoot)
    if (-not (Test-Path -LiteralPath $selectedRoot -PathType Container)) {
        throw "Selected source directory not found: $selectedRoot"
    }
    $selectedRows = @(Import-Csv -LiteralPath $SelectedManifestPath -Delimiter "`t" -Encoding UTF8)
    Assert-SelectedManifest -Rows $selectedRows
    $selectedHashLines = [System.Collections.Generic.List[string]]::new()
    $selectedHashLines.Add("output_id`tsha256`tbytes`tfile_name`tlanding_url")

    foreach ($row in $selectedRows) {
        $sourcePath = Join-Path $selectedRoot $row.file_name
        if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
            throw "Selected source is missing: $sourcePath"
        }
        $sourceFile = Get-Item -LiteralPath $sourcePath
        $actualHash = (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actualHash -ne $row.sha256.ToLowerInvariant()) {
            throw "SHA-256 mismatch for selected source $($row.file_name)"
        }

        $selectedBedPath = Join-Path $BedRoot ("selected_" + $row.output_id + ".wav")
        New-ContinuousBed -InputPath $sourcePath -OutputPath $selectedBedPath -SourceKey ("selected_" + $row.output_id)
        $selectedOutputDirectory = if ($row.category -eq "melody") {
            Join-Path $RepoRoot "app/src/main/assets/focus/melodies"
        } else {
            Join-Path $OutputRoot $row.category
        }
        New-Item -ItemType Directory -Path $selectedOutputDirectory -Force | Out-Null
        $selectedOutputPath = Join-Path $selectedOutputDirectory ($row.output_id + ".ogg")
        Export-SeamlessOgg `
            -InputPath $selectedBedPath `
            -OutputPath $selectedOutputPath `
            -OutputId $row.output_id `
            -ProvenanceComment "Pixabay source provenance: assets/focus/SELECTED_AUDIO_SOURCES.md"
        $selectedDuration = Get-AudioDurationSeconds -Path $selectedOutputPath
        if ([Math]::Abs($selectedDuration - $LoopSeconds) -gt 0.25) {
            throw "Unexpected selected output duration for $($row.output_id): $selectedDuration"
        }
        $selectedHashLines.Add(
            "$($row.output_id)`t$actualHash`t$($sourceFile.Length)`t$($row.file_name)`t$($row.landing_url)"
        )
    }

    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllLines($SelectedHashManifestPath, $selectedHashLines, $utf8NoBom)
    Write-Host "Prepared and verified $($selectedRows.Count) user-selected real-recording loops."
    Write-Host "Selected source hashes: $SelectedHashManifestPath"
} elseif ($SkipSelected) {
    Write-Warning "Prepared the CC0 base only; approved user-selected overrides were explicitly skipped."
}
