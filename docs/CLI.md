# ArchiveTool CLI — Usage and Demo Guide

The Kotlin CLI uses **Clikt** to parse commands and delegates all archive processing to the **Switchboard**. The CLI does **not** implement segmentation, OCR, human review or job-state persistence.

## Build and verify (Windows PowerShell)

From the repository root, after merging Switchboard through Git:

```powershell
.\gradlew.bat :app:classes
.\gradlew.bat :app:test
.\gradlew.bat :app:run --args="--help"
.\gradlew.bat ktfmtCheck
```

Gradle automatically resolves any missing dependencies when online. `:app:test` validates command parsing and helper functions; it **does not** prove that the full image pipeline works.

## Full workflow

```powershell
.\gradlew.bat :app:run --args='run -i "C:\Archive Photos\Batch 01" -o "C:\Archive Exports\result.csv"'
```

Run `-i` is optional and defaults to the **process working directory**. Using an absolute path is recommended for demos. The `-o` CSV output path is required.

Processing order:

```text
createPreview -> reviewSegmentationPreview -> manipulateImages
              -> createOCR -> reviewOCRPreview -> generateExport
```

`run` also supports:

- `--resume`: allow Switchboard to reuse an existing job state. Actual stage-resumption behaviour depends on the backend implementation.
- `-A, --auto-approve-threshold <0-100>`: validate an integer percentage, convert it to 0.0–1.0 and pass it to segmentation review. **The backend must actually honour the threshold**; the CLI does not decide which images are approved.

Example:

```powershell
.\gradlew.bat :app:run --args='run -i "C:\Archive Photos\Batch 01" -o "C:\Archive Exports\result.csv" --resume -A 95'
```

## Command reference

| Command | Arguments/options | Switchboard action |
| --- | --- | --- |
| `run` | `[-i DIR] -o FILE [--resume] [-A 0-100]` | All six stages in order |
| `preview create` | `[-i DIR]` | `createPreview` |
| `preview review` | `[-A 0-100]` | `reviewSegmentationPreview` |
| `ocr create` | none | `manipulateImages`, then `createOCR` |
| `ocr review` | none | `reviewOCRPreview` |
| `finalise` | `-o FILE` (required) | `generateExport` **only** |
| `status` | `[JOB_DIR]` | `getStatus` (backend implementation may be incomplete) |

`finalize` is an alias for `finalise`. The `-v` / `-q` root options control logging verbosity.

### Important working-directory rule for staged commands

`preview review`, `ocr create`, `ocr review` and `finalise` use the **current process working directory** as the job root; unlike `run`, they do not accept an `-i` option. `status` accepts an optional positional job directory.

When invoking through `:app:run`, the working directory can be determined by Gradle's JavaExec configuration, **not necessarily the PowerShell directory**. For an accurate staged demo, use the application launcher from the job directory.

First create the launch scripts from the repository root:

```powershell
.\gradlew.bat :app:installDist
```

Then change into the photo directory and invoke the generated launcher using its absolute path (adjust both paths):

```powershell
Set-Location "C:\Archive Photos\Batch 01"
& "C:\path\to\comp30022-uom-archives\app\build\install\app\bin\app.bat" preview create
& "C:\path\to\comp30022-uom-archives\app\build\install\app\bin\app.bat" preview review
& "C:\path\to\comp30022-uom-archives\app\build\install\app\bin\app.bat" ocr create
& "C:\path\to\comp30022-uom-archives\app\build\install\app\bin\app.bat" ocr review
& "C:\path\to\comp30022-uom-archives\app\build\install\app\bin\app.bat" finalise -o "C:\Archive Exports\result.csv"
```

The default Gradle application distribution name is `app` unless the project changes its application name.

Job state and intermediate files are under the input directory (for example `.archivetool_generated/job.json`). Do not use that internal directory as the job root.

## OCR configuration

The latest supplied Switchboard snapshot uses an online OCR implementation through an OpenAI-compatible SDK with a Google Gemini endpoint. Configure the **actual backend API key** before a real end-to-end demo. In PowerShell, set a key for the current session without committing it to Git:

```powershell
$env:ARCHIVETOOL_API_KEY = "<insert-key-locally>"
```

The default implementation requires internet access. The Java SDK is a **Gradle dependency**, not a separate program that the CLI user installs.

## What is and is not verified

**CLI code:** command routing, validation, path normalisation, percentage conversion and the intended Switchboard call order are represented in source and parser/unit tests.

**Backend:** the latest provided snapshot still has potential demo blockers that must be checked against Amy's next commit:

- `getStatus()` was unfinished in the last reviewed backend snapshot; do not rely on `status` for a demo until Amy completes it.
- The segmentation auto-approval threshold is forwarded by the CLI, but must be verified in the backend GUI.
- Structured OCR metadata conversion and real-image CSV content need an end-to-end check.
- `--resume` depends on persisted Switchboard state and stage eligibility, which parser tests do not exercise.

Demo checklist: build and tests pass; API key and connectivity checked; a small real input batch opens segmentation/OCR review as intended; CSV fields populated; previous working Git commit available as fallback.
