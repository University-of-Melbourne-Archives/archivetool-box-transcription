# ArchiveTool CLI — Usage and Demo Guide

ArchiveTool provides a Kotlin command-line interface (CLI) built with **Clikt**. It parses commands, validates options and delegates archive processing to **Switchboard**. Segmentation, image manipulation, OCR, human review, job-state persistence and CSV generation are implemented by the backend, not duplicated in the CLI.

## Build and check (Windows PowerShell)

Run these commands from the repository root:

```powershell
.\gradlew.bat :app:classes
.\gradlew.bat :app:test
.\gradlew.bat :app:run --args="--help"
.\gradlew.bat ktfmtCheck
```

Gradle downloads missing dependencies automatically when internet access is available. `:app:test` checks the help text, argument validation and command-to-Switchboard calls using a recording test backend. **It does not run actual segmentation, OCR, or GUI review.**

## Command reference

| Command | Options | Switchboard action |
| --- | --- | --- |
| `run` | `[-i DIR] -o FILE [--no-resume] [-A 0-100]` | Run all six processing stages in sequence |
| `preview create` | `[-i DIR] [--no-resume]` | `createPreview` |
| `preview review` | `[-i DIR] [-A 0-100]` | `reviewSegmentationPreview` |
| `ocr create` | `[-i DIR]` | `manipulateImages`, then `createOCR` |
| `ocr review` | `[-i DIR]` | `reviewOCRPreview` |
| `status` | `[-i DIR]` | `getStatus` (prints job progress from Switchboard) |
| `finalise` | `[-i DIR] -o FILE` | `generateExport` **only** |

**Shared input-directory option:** `-i, --input-directory` selects the job root containing the source images and persisted state. It is optional for the commands above; if omitted, the default is the process **current working directory (`.`)**. The supplied directory must exist and be readable. Do not pass `.archivetool_generated` itself as the job root.

**Output:** `-o, --output` is required for `run` and `finalise`, and specifies the destination CSV file. `finalize` is an alternative spelling of `finalise`.

**Logging:** The root command supports `-v`/`--verbose` and `-q`/`--quiet` (repeat them to adjust logging level). For example, `-v run ...` puts the root option before the subcommand.

## Full workflow

```powershell
.\gradlew.bat :app:run --args='run -i "C:\Archive Photos\Batch 01" -o "C:\Archive Exports\result.csv"'
```

The `run` command calls Switchboard in this order:

```text
createPreview
  -> reviewSegmentationPreview
  -> manipulateImages
  -> createOCR
  -> reviewOCRPreview
  -> generateExport
```

`run` and `preview create` **enable resume by default**: the CLI passes `permitExisting = true` to `createPreview`, allowing Switchboard to reuse existing job state. Pass `--no-resume` to request `permitExisting = false` instead. **This flag does not erase a job**; if a job already exists, Switchboard may reject creation rather than overwrite it. Whether finished work is skipped depends on backend eligibility rules.

```powershell
.\gradlew.bat :app:run --args='preview create -i "C:\Archive Photos\Batch 01" --no-resume'
```

For `run` and `preview review`, `-A, --auto-approve-threshold` accepts an integer percentage between 0 and 100; the CLI converts it to a value between 0.0 and 1.0 for Switchboard. Approval decisions are made by the backend, not the CLI.

## Individual commands

Each stage can target the **same directory explicitly**, without changing the terminal working directory:

```powershell
.\gradlew.bat :app:run --args='preview create -i "C:\Archive Photos\Batch 01"'
.\gradlew.bat :app:run --args='preview review -i "C:\Archive Photos\Batch 01"'
.\gradlew.bat :app:run --args='ocr create -i "C:\Archive Photos\Batch 01"'
.\gradlew.bat :app:run --args='ocr review -i "C:\Archive Photos\Batch 01"'
.\gradlew.bat :app:run --args='status -i "C:\Archive Photos\Batch 01"'
.\gradlew.bat :app:run --args='finalise -i "C:\Archive Photos\Batch 01" -o "C:\Archive Exports\result.csv"'
```

These commands represent *successive stages* of the pipeline. Do not execute `ocr create`, `ocr review`, or `finalise` on an unprocessed directory and expect completed output.

`status` is now implemented in the reviewed Switchboard code and reports progress from the saved job state. Its stage counts reflect the backend's own classification rules. `finalise` **does not rerun segmentation, image manipulation, OCR or review**: it only exports existing processed records.

## Installed command (without Gradle on every invocation)

For a demo, generate the local application distribution from the repository root:

```powershell
.\gradlew.bat :app:installDist
$launcher = (Get-ChildItem .\app\build\install -Recurse -Filter archivetool.bat | Select-Object -First 1).FullName
& $launcher --help
```

The script name is configured as `archivetool.bat`; this example locates it rather than assuming an installation-directory name. Once built, use the same commands without the `:app:run --args=` wrapper:

```powershell
& $launcher status -i "C:\Archive Photos\Batch 01"
& $launcher finalise -i "C:\Archive Photos\Batch 01" -o "C:\Archive Exports\result.csv"
```

## OCR environment and demo preparation

The reviewed Switchboard version uses an online OCR implementation through an OpenAI-compatible Java SDK targeting a Gemini endpoint. Real OCR requires a working API key and internet access. In a PowerShell session, set the key locally; **never commit it to Git**:

```powershell
$env:ARCHIVETOOL_API_KEY = "<insert-key-locally>"
```

Use a small, previously tested image set for the demo, and coordinate the segmentation/OCR review windows with the presenters. Verify the CSV **contents** as well as the file's existence. Persisted job data is stored under the chosen input directory (e.g., `.archivetool_generated/job.json`).

## Verification boundary

- **CLI tests:** command registration, help, input validation, default options, and calls to the processing stages using a recording test backend.
- **Integration / end-to-end:** needs separate verification of real images, GUI review, OCR responses, saved job state and final CSV fields. Passing the CLI unit tests alone does not establish these outcomes.
- **Resume:** CLI defaults and parameter parsing are covered; correct backend recovery from a partially processed job must be checked separately.

For a final demo, confirm build/tests, API key and connectivity, a representative review flow, populated CSV output and a stable Git commit to fall back to.

## CLI tests for the archivist user story

**User story:** “As an archivist, I need a usable interface that facilitates the usage of the software.”

The main automated tests are in `app/src/test/kotlin/CliCommandTest.kt`. They test the CLI from the user's point of view: finding commands, entering arguments, and choosing which processing stage to run. The tests use a small recording backend, so we can check which Switchboard stage would be called without opening GUI windows or using the OCR API.

| User task | Automated checks | What we expect |
| --- | --- | --- |
| Find out how to use the tool | Main, `run`, `preview`, `ocr`, and `finalise` help tests | Relevant commands and options are visible |
| Recover from an input mistake | Missing `-o`, missing input directory, invalid thresholds, unknown command | Command returns an error and processing does not start |
| Process a whole batch | `run` stage-order test | Six stages are requested in the correct order |
| Work on one stage at a time | `preview`, `ocr`, `status`, `finalise` tests | Only the expected stage(s) are requested |
| Resume existing work | Resume/default and `--no-resume` tests | Job reuse is on by default and can be disabled |
| Choose job and output locations | Input/output path tests | Normalised paths are passed to Switchboard |
| Review confidence handling | Threshold range and conversion tests | 0–100% is accepted and passed as a fraction |

**What still needs manual testing:** Run the real application on an archive image batch, check whether the steps and help messages are understandable to an archivist, review boxes and OCR in the GUI, resume a partially processed batch, and open the exported CSV to check its contents. These are separate acceptance/integration checks. CLI tests alone cannot establish usability for every user or prove the backend worked correctly.
