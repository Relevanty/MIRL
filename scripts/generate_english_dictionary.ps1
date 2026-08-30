[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$TeiPath,
    [Parameter(Mandatory = $true)]
    [string]$FrequencyPath,
    [Parameter(Mandatory = $true)]
    [string]$OutputPath,
    [Parameter(Mandatory = $true)]
    [string]$CopyingPath,
    [Parameter(Mandatory = $true)]
    [string]$NoticeOutputPath,
    [Parameter(Mandatory = $true)]
    [string]$CopyingOutputPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

foreach ($inputPath in @($TeiPath, $FrequencyPath, $CopyingPath)) {
    if (-not (Test-Path -LiteralPath $inputPath)) {
        throw "Input file not found: $inputPath"
    }
}

$frequencyWords = [System.Collections.Generic.List[string]]::new()
$frequencyRanks = [System.Collections.Generic.Dictionary[string, int]]::new(
    [System.StringComparer]::OrdinalIgnoreCase
)
$rank = 0
foreach ($line in [System.IO.File]::ReadLines((Resolve-Path -LiteralPath $FrequencyPath))) {
    $rank++
    $word = $line.Trim().ToLowerInvariant()
    if ($word -notmatch "^[a-z]+(?:'[a-z]+)?$") { continue }
    if ($word.Length -lt 2 -or $word.Length -gt 24) { continue }
    if (-not $frequencyRanks.ContainsKey($word)) {
        $frequencyRanks.Add($word, $rank)
        $frequencyWords.Add($word)
    }
}

$blockedWords = [System.Collections.Generic.HashSet[string]]::new(
    [System.StringComparer]::OrdinalIgnoreCase
)
@(
    'anal', 'anus', 'bastard', 'bitch', 'blowjob', 'bollocks', 'boner', 'boob',
    'bullshit', 'buttplug', 'cocksucker', 'cunt', 'dick', 'dildo', 'fag',
    'faggot', 'fuck', 'fucker', 'fucking', 'handjob', 'hentai', 'jerkoff',
    'motherfucker', 'nigger', 'nigga', 'penis', 'porn', 'pornography', 'pussy',
    'rape', 'rapist', 'retard', 'san', 'shit', 'slut', 'testicle', 'tits', 'vagina',
    'wank', 'whore'
) | ForEach-Object { [void]$blockedWords.Add($_) }

# FreeDict is sense-oriented, so ultra-common function words can otherwise expose
# an archaic secondary sense first. These compact overrides only correct that
# presentation order; every headword still has to exist in the source dictionary.
$translationOverrides = @{
    'the'='определённый артикль'; 'of'='из; о; от'; 'and'='и; а'; 'in'='в; внутри'
    'for'='для; за; в течение'; 'as'='как; в качестве'; 'on'='на; включённый'
    'with'='с; вместе с'; 'by'='у; посредством'; 'he'='он'; 'at'='в; у; на'
    'from'='из; от; с'; 'his'='его; свой'; 'an'='неопределённый артикль'
    'were'='были'; 'are'='являются; есть'; 'which'='который; какой'; 'also'='также; тоже'
    'or'='или; либо'; 'has'='имеет'; 'had'='имел; было'; 'first'='первый; сначала'
    'one'='один'; 'their'='их'; 'its'='его; её (о предмете)'; 'after'='после'
    'new'='новый'; 'who'='кто; который'; 'they'='они'; 'two'='два'
    'her'='её; ей'; 'she'='она'; 'been'='был; являлся'; 'other'='другой'
    'when'='когда'; 'time'='время; раз'; 'more'='больше; ещё'; 'may'='мочь; возможно'
    'people'='люди; народ'; 'into'='внутрь; в'; 'over'='над; через; более'
    'only'='только; единственный'; 'most'='большинство; самый'; 'such'='такой'
    'through'='через; сквозь'; 'about'='о; около'; 'between'='между'
    'before'='до; перед'; 'well'='хорошо; колодец'; 'known'='известный'
    'three'='три'; 'would'='бы; стал бы'; 'where'='где; куда'; 'later'='позже'
    'during'='во время'; 'including'='включая'; 'became'='стал; стала'
    'under'='под; менее'; 'used'='использовал; использованный'; 'many'='много; многие'
    'however'='однако'; 'while'='пока; в то время как'; 'then'='тогда; затем'
    'work'='работа; работать'; 'house'='дом'; 'world'='мир'; 'life'='жизнь'
    'day'='день'; 'year'='год'; 'years'='годы'; 'part'='часть'; 'made'='сделал; сделанный'
    'called'='назвал; называемый'; 'same'='тот же; одинаковый'; 'each'='каждый'
    'around'='вокруг; около'; 'because'='потому что'; 'against'='против'
    'second'='второй; секунда'; 'city'='город'; 'state'='состояние; штат; государство'
    'name'='имя; название'; 'group'='группа'; 'film'='фильм'; 'number'='число; номер'
    'music'='музыка'; 'team'='команда'; 'school'='школа'; 'book'='книга'
    'game'='игра'; 'family'='семья'; 'home'='дом; домой'; 'water'='вода'
    'place'='место; размещать'; 'system'='система'; 'company'='компания'
    'found'='нашёл; найденный; основал'; 'public'='общественный; публика'
    'be'='быть; находиться'; 'is'='есть; является'; 'was'='был; была; было'
    'can'='мочь; уметь'; 'will'='будет; воля'; 'high'='высокий; высоко'
}

$coreMetadataOverrides = @{
    'the'=[pscustomobject]@{ Translation='определённый артикль'; Hint='Used before a noun when the listener knows which person or thing is meant.'; Pronunciation='/ðə; ðiː/'; PartOfSpeech='article' }
    'be'=[pscustomobject]@{ Translation='быть; находиться'; Hint='To exist, occur, or have a particular state or quality.'; Pronunciation='/biː/'; PartOfSpeech='verb' }
    'is'=[pscustomobject]@{ Translation='есть; является'; Hint='The third-person singular present form of be.'; Pronunciation='/ɪz/'; PartOfSpeech='verb' }
    'are'=[pscustomobject]@{ Translation='есть; являются'; Hint='The present plural and second-person singular form of be.'; Pronunciation='/ɑːr/'; PartOfSpeech='verb' }
    'was'=[pscustomobject]@{ Translation='был; была; было'; Hint='The first- and third-person singular past form of be.'; Pronunciation='/wɒz/'; PartOfSpeech='verb' }
    'were'=[pscustomobject]@{ Translation='были; был'; Hint='The plural and second-person singular past form of be.'; Pronunciation='/wɜːr/'; PartOfSpeech='verb' }
    'on'=[pscustomobject]@{ Translation='на; включённый'; Hint='Touching or supported by a surface; also operating or active.'; Pronunciation='/ɒn/'; PartOfSpeech='preposition' }
    'or'=[pscustomobject]@{ Translation='или; либо'; Hint='Used to connect alternatives or possibilities.'; Pronunciation='/ɔːr/'; PartOfSpeech='conjunction' }
    'one'=[pscustomobject]@{ Translation='один; единица'; Hint='The number 1, or a single person or thing.'; Pronunciation='/wʌn/'; PartOfSpeech='numeral' }
    'may'=[pscustomobject]@{ Translation='мочь; возможно'; Hint='Expresses possibility or permission.'; Pronunciation='/meɪ/'; PartOfSpeech='modal verb' }
    'can'=[pscustomobject]@{ Translation='мочь; уметь'; Hint='Expresses ability or possibility.'; Pronunciation='/kæn/'; PartOfSpeech='modal verb' }
    'will'=[pscustomobject]@{ Translation='будет; намереваться'; Hint='Expresses the future, willingness, or intention.'; Pronunciation='/wɪl/'; PartOfSpeech='modal verb' }
    'high'=[pscustomobject]@{ Translation='высокий; высоко'; Hint='Extending a long way above the ground or above an average level.'; Pronunciation='/haɪ/'; PartOfSpeech='adjective' }
    'who'=[pscustomobject]@{ Translation='кто; который'; Hint='Asks which person, or introduces a clause about a person.'; Pronunciation='/huː/'; PartOfSpeech='pronoun' }
    'season'=[pscustomobject]@{ Translation='сезон; время года'; Hint='One of the four periods of the year, or a regular period for an activity.'; Pronunciation='/ˈsiːzən/'; PartOfSpeech='noun' }
    'based'=[pscustomobject]@{ Translation='основанный; базирующийся'; Hint='Having a specified foundation, origin, or main location.'; Pronunciation='/beɪst/'; PartOfSpeech='adjective' }
    'men'=[pscustomobject]@{ Translation='мужчины; люди'; Hint='The plural form of man.'; Pronunciation='/mɛn/'; PartOfSpeech='noun' }
    'german'=[pscustomobject]@{ Translation='немецкий; немец; немецкий язык'; Hint='Related to Germany, its people, or their language.'; Pronunciation='/ˈdʒɜːrmən/'; PartOfSpeech='adjective' }
}

# zxcvbn's password-oriented Wikipedia list intentionally omits a few extremely
# common short verbs. Give these reviewed forms conservative corpus-like ranks so
# they stay in the foundation band instead of being mislabeled as rare.
$coreFrequencyRanks = @{ 'be'=5; 'can'=45; 'will'=55 }
foreach ($pair in $coreFrequencyRanks.GetEnumerator()) {
    if (-not $frequencyRanks.ContainsKey($pair.Key)) {
        $frequencyRanks.Add($pair.Key, $pair.Value)
        $frequencyWords.Add($pair.Key)
    }
}

$blockedTranslationPattern = '(?i)(?:\bхуй|хуя|хуе|пизд|бляд|блять|ебат|ёб|мудак|жоп|говн|дроч|залуп|сперм|порн|проститут|трах)'

$wanted = [System.Collections.Generic.HashSet[string]]::new(
    $frequencyWords,
    [System.StringComparer]::OrdinalIgnoreCase
)
$entries = [System.Collections.Generic.Dictionary[string, object]]::new(
    [System.StringComparer]::OrdinalIgnoreCase
)
$teiNamespaceUri = 'http://www.tei-c.org/ns/1.0'
$orthName = [System.Xml.Linq.XName]::Get('orth', $teiNamespaceUri)
$positionName = [System.Xml.Linq.XName]::Get('pos', $teiNamespaceUri)
$citationName = [System.Xml.Linq.XName]::Get('cit', $teiNamespaceUri)
$quoteName = [System.Xml.Linq.XName]::Get('quote', $teiNamespaceUri)
$definitionName = [System.Xml.Linq.XName]::Get('def', $teiNamespaceUri)
$pronunciationName = [System.Xml.Linq.XName]::Get('pron', $teiNamespaceUri)
$xmlLanguage = [System.Xml.Linq.XName]::Get('lang', 'http://www.w3.org/XML/1998/namespace')

function Clean-Text([string]$value, [int]$maxLength) {
    if ([string]::IsNullOrWhiteSpace($value)) { return '' }
    $clean = $value.Replace(([string][char]0x0301), '')
    $clean = [System.Net.WebUtility]::HtmlDecode($clean)
    $clean = $clean.Replace('[[', '').Replace(']]', '')
    $clean = [System.Text.RegularExpressions.Regex]::Replace($clean, '\{\{[^}]+\}\}', '')
    $clean = [System.Text.RegularExpressions.Regex]::Replace($clean, '\s+', ' ').Trim()
    $clean = $clean.Replace("`t", ' ').Replace("`r", ' ').Replace("`n", ' ')
    if ($clean.Length -gt $maxLength) {
        $clean = $clean.Substring(0, $maxLength).TrimEnd() + '…'
    }
    return $clean
}

$settings = [System.Xml.XmlReaderSettings]::new()
$settings.DtdProcessing = [System.Xml.DtdProcessing]::Ignore
$settings.IgnoreComments = $true
$settings.IgnoreWhitespace = $true
$reader = [System.Xml.XmlReader]::Create((Resolve-Path -LiteralPath $TeiPath), $settings)
try {
    while (-not $reader.EOF) {
        if ($reader.NodeType -eq [System.Xml.XmlNodeType]::Element -and $reader.LocalName -eq 'entry') {
            $rawEntry = $reader.ReadOuterXml()
            if ([string]::IsNullOrWhiteSpace($rawEntry)) { continue }
            $entry = [System.Xml.Linq.XElement]::Parse($rawEntry)
            $orthElement = $entry.Descendants($orthName) | Select-Object -First 1
            if ($null -eq $orthElement) { continue }
            $orthOriginal = [string]$orthElement.Value
            $word = $orthOriginal.Trim().ToLowerInvariant()
            if ($orthOriginal -cnotmatch "^[a-z]+(?:'[a-z]+)?$") { continue }
            if ($blockedWords.Contains($word)) { continue }

            $partsOfSpeech = @(
                $entry.Descendants($positionName) |
                    ForEach-Object { $_.Value.Trim().ToLowerInvariant() } |
                    Where-Object { $_ }
            )
            if ($partsOfSpeech.Count -gt 0 -and @($partsOfSpeech | Where-Object { $_ -ne 'pn' }).Count -eq 0) {
                continue
            }

            $translations = [System.Collections.Generic.List[string]]::new()
            foreach ($citation in $entry.Descendants($citationName)) {
                $typeAttribute = $citation.Attribute('type')
                $languageAttribute = $citation.Attribute($xmlLanguage)
                $type = if ($null -eq $typeAttribute) { '' } else { $typeAttribute.Value }
                $language = if ($null -eq $languageAttribute) { '' } else { $languageAttribute.Value }
                if ($type -ne 'trans' -and $language -ne 'ru') { continue }
                foreach ($quote in $citation.Descendants($quoteName)) {
                    $translation = Clean-Text $quote.Value 90
                    if ($translation -and $translation -notmatch '(?i)\b(?:мат|неценз|оскорб)' -and $translation -notmatch $blockedTranslationPattern) {
                        $translations.Add($translation)
                    }
                }
            }
            if ($translations.Count -eq 0) { continue }

            $definition = $entry.Descendants($definitionName) | Select-Object -First 1
            $pronunciation = $entry.Descendants($pronunciationName) | Select-Object -First 1
            $part = $partsOfSpeech | Where-Object { $_ -ne 'pn' } | Select-Object -First 1
            if (-not $entries.ContainsKey($word)) {
                $entries.Add($word, [pscustomobject]@{
                    TranslationCounts = [System.Collections.Generic.Dictionary[string, int]]::new(
                        [System.StringComparer]::OrdinalIgnoreCase
                    )
                    Hint = if ($null -eq $definition) { '' } else { Clean-Text $definition.Value 180 }
                    Pronunciation = if ($null -eq $pronunciation) { '' } else { Clean-Text $pronunciation.Value 60 }
                    PartOfSpeech = if ($part) { [string]$part } else { '' }
                    SenseScore = 0
                    BestEntryScore = [int]::MinValue
                })
            }
            $record = $entries[$word]
            foreach ($translation in $translations) {
                if ($record.TranslationCounts.ContainsKey($translation)) {
                    $record.TranslationCounts[$translation]++
                } else {
                    $record.TranslationCounts.Add($translation, 1)
                }
            }
            $definitionText = if ($null -eq $definition) { '' } else { Clean-Text $definition.Value 180 }
            $pronunciationText = if ($null -eq $pronunciation) { '' } else { Clean-Text $pronunciation.Value 60 }
            $entryScore = $translations.Count * 4
            if ($definitionText) { $entryScore += 8 }
            if ($pronunciationText) { $entryScore += 2 }
            if ($part -in @('suffix', 'prefix', 'pn', 'symbol', 'letter')) { $entryScore -= 100 }
            if ($definitionText -match '(?i)\b(?:obsolete|archaic|rare|dated|slang|vulgar|offensive|tincture|heraldry|deprecated)\b') { $entryScore -= 60 }
            if ($entryScore -gt $record.BestEntryScore) {
                $record.BestEntryScore = $entryScore
                $record.Hint = $definitionText
                $record.Pronunciation = $pronunciationText
                $record.PartOfSpeech = if ($part) { [string]$part } else { '' }
            }
            $record.SenseScore += [Math]::Max(1, $translations.Count)
            continue
        }
        [void]$reader.Read()
    }
} finally {
    $reader.Dispose()
}

# A few inflected function words are intentionally absent as FreeDict headwords.
# Add them from the reviewed core metadata so a frequency course never skips
# essential forms such as is/was/were.
foreach ($pair in $coreMetadataOverrides.GetEnumerator()) {
    if (-not $frequencyRanks.ContainsKey($pair.Key) -or $entries.ContainsKey($pair.Key)) { continue }
    $translationCounts = [System.Collections.Generic.Dictionary[string, int]]::new(
        [System.StringComparer]::OrdinalIgnoreCase
    )
    $translationCounts.Add($pair.Value.Translation, 1)
    $entries.Add($pair.Key, [pscustomobject]@{
        TranslationCounts = $translationCounts
        Hint = $pair.Value.Hint
        Pronunciation = $pair.Value.Pronunciation
        PartOfSpeech = $pair.Value.PartOfSpeech
        SenseScore = 1
        BestEntryScore = 1000
    })
}

$selected = [System.Collections.Generic.List[object]]::new()
$selectedWords = [System.Collections.Generic.HashSet[string]]::new(
    [System.StringComparer]::OrdinalIgnoreCase
)
foreach ($word in ($frequencyWords | Sort-Object { $frequencyRanks[$_] })) {
    if ($selected.Count -ge 10000) { break }
    if (-not $entries.ContainsKey($word)) { continue }
    $record = $entries[$word]
    $coreMetadata = if ($coreMetadataOverrides.ContainsKey($word)) { $coreMetadataOverrides[$word] } else { $null }
    $translation = if ($null -ne $coreMetadata) {
        $coreMetadata.Translation
    } elseif ($translationOverrides.ContainsKey($word)) {
        $translationOverrides[$word]
    } else {
        (($record.TranslationCounts.GetEnumerator() |
            Sort-Object @{Expression = 'Value'; Descending = $true}, @{Expression = { $_.Key.Length }; Descending = $false} |
            Select-Object -First 4 |
            ForEach-Object { $_.Key }) -join '; ')
    }
    if ([string]::IsNullOrWhiteSpace($translation)) { continue }
    $selected.Add([pscustomobject]@{
        Word = $word
        FrequencyRank = $frequencyRanks[$word]
        Translation = Clean-Text $translation 220
        Hint = if ($null -ne $coreMetadata) { $coreMetadata.Hint } else { $record.Hint }
        Pronunciation = if ($null -ne $coreMetadata) { $coreMetadata.Pronunciation } else { $record.Pronunciation }
        PartOfSpeech = if ($null -ne $coreMetadata) { $coreMetadata.PartOfSpeech } else { $record.PartOfSpeech }
    })
    [void]$selectedWords.Add($word)
}

$rankedSelectedCount = $selected.Count
if ($selected.Count -lt 10000) {
    $backfill = foreach ($pair in $entries.GetEnumerator()) {
        if ($selectedWords.Contains($pair.Key)) { continue }
        $coreMetadata = if ($coreMetadataOverrides.ContainsKey($pair.Key)) { $coreMetadataOverrides[$pair.Key] } else { $null }
        $translation = if ($null -ne $coreMetadata) {
            $coreMetadata.Translation
        } else {
            (($pair.Value.TranslationCounts.GetEnumerator() |
                Sort-Object @{Expression = 'Value'; Descending = $true}, @{Expression = { $_.Key.Length }; Descending = $false} |
                Select-Object -First 4 |
                ForEach-Object { $_.Key }) -join '; ')
        }
        if ([string]::IsNullOrWhiteSpace($translation)) { continue }
        [pscustomobject]@{
            Word = $pair.Key
            FrequencyRank = 100000
            Translation = Clean-Text $translation 220
            Hint = if ($null -ne $coreMetadata) { $coreMetadata.Hint } else { $pair.Value.Hint }
            Pronunciation = if ($null -ne $coreMetadata) { $coreMetadata.Pronunciation } else { $pair.Value.Pronunciation }
            PartOfSpeech = if ($null -ne $coreMetadata) { $coreMetadata.PartOfSpeech } else { $pair.Value.PartOfSpeech }
            SenseScore = if ($null -ne $coreMetadata) { 1000000 } else { $pair.Value.SenseScore }
            WordLength = $pair.Key.Length
        }
    }
    foreach ($item in ($backfill | Sort-Object @{Expression = 'SenseScore'; Descending = $true}, @{Expression = 'WordLength'; Descending = $false}, Word)) {
        if ($selected.Count -ge 10000) { break }
        $item.FrequencyRank = 100000 + $selected.Count - $rankedSelectedCount + 1
        $selected.Add($item)
        [void]$selectedWords.Add($item.Word)
    }
}

if ($selected.Count -ne 10000) {
    throw "Expected exactly 10000 ranked translated words, found $($selected.Count)"
}

$outputDirectory = Split-Path -Parent $OutputPath
$noticeDirectory = Split-Path -Parent $NoticeOutputPath
[System.IO.Directory]::CreateDirectory($outputDirectory) | Out-Null
[System.IO.Directory]::CreateDirectory($noticeDirectory) | Out-Null

$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$writer = [System.IO.StreamWriter]::new($OutputPath, $false, $utf8NoBom)
try {
    $writer.WriteLine('# id\tfrequencyRank\tlevel\tword\ttranslation\thint\tpronunciation\tpartOfSpeech')
    for ($index = 0; $index -lt $selected.Count; $index++) {
        $item = $selected[$index]
        $position = $index + 1
        $level = if ($position -le 1500) { 'BASE' } elseif ($position -le 3500) { 'COMMON' } elseif ($position -le 6000) { 'CONFIDENT' } elseif ($position -le 8500) { 'ADVANCED' } else { 'RARE' }
        $columns = @(
            $position,
            $item.FrequencyRank,
            $level,
            $item.Word,
            $item.Translation,
            $item.Hint,
            $item.Pronunciation,
            $item.PartOfSpeech
        )
        $writer.WriteLine(($columns -join "`t"))
    }
} finally {
    $writer.Dispose()
}

$notice = @"
MIRL offline English vocabulary dataset
========================================

Generated on 2026-08-29. Contains exactly 10,000 unique single-word English
headwords with Russian translations. MIRL stores this data locally and never
sends study progress or audio to a server.

Translations, definitions and pronunciation data
-------------------------------------------------
English-Russian FreeDict+WikDict dictionary, edition 2025.11.23
(generated/published 2025-11-24 distribution).
Source: https://freedict.org/downloads/#dictionary-downloads
Upstream data: Wiktionary via DBnary/WikDict.
License: Creative Commons Attribution-ShareAlike 3.0 Unported (CC BY-SA 3.0).
The complete upstream COPYING text is included as FREEDICT_COPYING.txt.

Frequency ranking
-----------------
The headword order is derived from the English Wikipedia frequency list shipped
with Dropbox zxcvbn. Source: https://github.com/dropbox/zxcvbn
Copyright (c) 2012-2016 Dropbox, Inc. zxcvbn is distributed under the MIT License;
the complete notice is included as ZXCVBN_LICENSE.txt.

Processing
----------
Only lowercase single-word Latin headwords present in both sources were kept.
Proper nouns (`pn`), entries without a Russian translation, malformed tokens and
an explicit offensive-language denylist were excluded. Duplicate headwords and
duplicate translations were merged. Words missing from the zxcvbn list were used
only to fill the final rare band, prioritising well-described, multi-sense entries.
The five labels are MIRL frequency bands, not official CEFR certification.
"@
[System.IO.File]::WriteAllText($NoticeOutputPath, $notice, $utf8NoBom)
[System.IO.File]::Copy($CopyingPath, $CopyingOutputPath, $true)

Write-Output "frequencyCandidates=$($frequencyWords.Count)"
Write-Output "translatedDictionaryMatches=$($entries.get_Count())"
Write-Output "selectedUniqueWords=$($selected.Count)"
Write-Output "rowsWithRussianTranslation=$(@($selected | Where-Object { $_.Translation }).Count)"
Write-Output "directlyRankedWords=$rankedSelectedCount"
Write-Output "dictionaryBackfillWords=$(10000 - $rankedSelectedCount)"
Write-Output "outputBytes=$((Get-Item -LiteralPath $OutputPath).Length)"
