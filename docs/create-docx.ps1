$ErrorActionPreference = 'Stop'
$source = Join-Path $PSScriptRoot 'RetailCore-Beginner-Guide.rtf'
$target = Join-Path $PSScriptRoot 'RetailCore-Beginner-Guide.docx'
$rtf = [System.IO.File]::ReadAllText($source)

# Convert the guide's RTF paragraphs to readable text, then package them as a
# valid WordprocessingML document. Heading styles are assigned to numbered sections.
$rtf = [regex]::Replace($rtf, '\\(fonttbl|colortbl|header|footer)\b.*?\r?\n', '')
$rtf = $rtf -replace '\\par\b', "`n"
$rtf = $rtf -replace '\\tab\b', "`t"
$rtf = $rtf -replace '\\line\b', "`n"
$rtf = [regex]::Replace($rtf, "\\'[0-9a-fA-F]{2}", '')
$rtf = [regex]::Replace($rtf, '\\[a-zA-Z]+-?\d* ?', '')
$rtf = $rtf -replace '\\([{}\\])', '$1'
$rtf = $rtf -replace '[{}]', ''
$lines = $rtf -split "`r?`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ }

$paragraphs = foreach ($line in $lines) {
    $style = 'Normal'
    if ($line -match '^\d+\.\s') { $style = 'Heading1' }
    elseif ($line -match '^(Cashier|Manager|Products\.|Sales\.|Stock movements\.|Other records\.|Strategy pattern|Factory pattern|Repository pattern|Observer pattern|Command pattern|Layered architecture|Dependency injection|Automated tests|Manual checks|SKU\s|Cart / sale|Stock\s|Reorder level|VAT / tax|Repository\s|Pattern\s|JSON\s)') { $style = 'Heading2' }
    $escaped = [System.Security.SecurityElement]::Escape($line)
    if ($style -eq 'Normal') {
        "<w:p><w:pPr><w:spacing w:after='120'/></w:pPr><w:r><w:t xml:space='preserve'>$escaped</w:t></w:r></w:p>"
    } else {
        "<w:p><w:pPr><w:pStyle w:val='$style'/></w:pPr><w:r><w:t xml:space='preserve'>$escaped</w:t></w:r></w:p>"
    }
}

$document = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$($paragraphs -join '')<w:sectPr><w:pgSz w:w="12240" w:h="15840"/><w:pgMar w:top="1200" w:right="1440" w:bottom="1200" w:left="1440"/></w:sectPr></w:body></w:document>
"@
$styles = @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Aptos" w:hAnsi="Aptos"/><w:sz w:val="22"/></w:rPr></w:rPrDefault></w:docDefaults>
<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>
<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:pPr><w:keepNext/><w:spacing w:before="300" w:after="140"/></w:pPr><w:rPr><w:b/><w:color w:val="1F4E79"/><w:sz w:val="30"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:basedOn w:val="Normal"/><w:pPr><w:keepNext/><w:spacing w:before="180" w:after="80"/></w:pPr><w:rPr><w:b/><w:color w:val="1F4E79"/><w:sz w:val="24"/></w:rPr></w:style>
</w:styles>
'@
$contentTypes = @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/></Types>
'@
$rels = @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>
'@
$docRels = @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>
'@

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target }
$file = [System.IO.File]::Open($target, [System.IO.FileMode]::CreateNew)
$zip = [System.IO.Compression.ZipArchive]::new($file, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($entry in @{
        '[Content_Types].xml' = $contentTypes
        '_rels/.rels' = $rels
        'word/document.xml' = $document
        'word/styles.xml' = $styles
        'word/_rels/document.xml.rels' = $docRels
    }.GetEnumerator()) {
        $part = $zip.CreateEntry($entry.Key)
        $stream = $part.Open()
        try {
            $writer = [System.IO.StreamWriter]::new($stream, [System.Text.UTF8Encoding]::new($false))
            $writer.Write($entry.Value)
            $writer.Dispose()
        } finally { $stream.Dispose() }
    }
} finally { $zip.Dispose(); $file.Dispose() }
Get-Item -LiteralPath $target | Select-Object FullName, Length
