# ArchiveTool CLI User Guide

ArchiveTool processes archive images through preview generation, review, image processing, OCR, OCR review, and CSV export.

> **Note:** ArchiveTool is still under development. Some backend stages may not yet be fully available in the current build.

## Quick Start

Process a directory of archive images and write the result to a CSV file:

```bash
archivetool run -i <input-directory> -o <output.csv>
```

Example:

```bash
archivetool run -i ./photos -o ./result.csv
```

If `-i` is omitted, ArchiveTool uses the current working directory.

## Command Summary

| Command | Purpose |
| --- | --- |
| `archivetool run` | Run the complete ArchiveTool workflow |
| `archivetool preview create` | Create detection previews for input images |
| `archivetool preview review` | Open the preview review stage |
| `archivetool ocr create` | Prepare approved images and generate OCR results |
| `archivetool ocr review` | Open the OCR review stage |
| `archivetool status` | Show the status of a job |
| `archivetool finalise` | Export the completed job to CSV |
| `archivetool --help` | Show general CLI help |

## `run`

Runs the complete workflow for an input directory.

The workflow order is:

```text
preview creation -> preview review -> image processing -> OCR -> OCR review -> CSV export
```

```bash
archivetool run [-i <input-directory>] -o <output.csv> [--resume] [-A <0-100>]
```

Options:

- `-i, --input-directory <directory>`  
  Directory containing the input images. Defaults to the current working directory.

- `-o, --output <output.csv>`  
  CSV output path. Required.

- `--resume`  
  Allow ArchiveTool to continue using an existing job in the input directory.

- `-A, --auto-approve-threshold <0-100>`  
  Auto-approval threshold expressed as a percentage.

Examples:

```bash
archivetool run -i ./photos -o ./result.csv
```

```bash
archivetool run -i ./photos -o ./result.csv -A 95
```

```bash
archivetool run -i ./photos -o ./result.csv --resume
```

## `preview create`

Creates preview data for images in an input directory.

```bash
archivetool preview create [-i <input-directory>]
```

Options:

- `-i, --input-directory <directory>`  
  Directory containing the input images. Defaults to the current working directory.

Example:

```bash
archivetool preview create -i ./photos
```

Or, when already inside the image directory:

```bash
archivetool preview create
```

## `preview review`

Opens the preview review stage for the job in the current working directory.

```bash
archivetool preview review [-A <0-100>]
```

Options:

- `-A, --auto-approve-threshold <0-100>`  
  Auto-approval threshold expressed as a percentage.

Example:

```bash
archivetool preview review -A 95
```

`preview review` does not take a job-directory argument. Run it from the job's input-photo directory.

## `ocr create`

Prepares approved images and generates OCR results for the job in the current working directory.

```bash
archivetool ocr create
```

This command runs the image-manipulation stage first, then calls the Switchboard OCR generation stage. Run it after segmentation review is complete.

## `ocr review`

Opens the OCR review stage for the job in the current working directory.

```bash
archivetool ocr review
```

`ocr review` calls the Switchboard OCR review stage directly. Run it after OCR results have been generated.

## `status`

Shows the status of an ArchiveTool job.

```bash
archivetool status [<job-directory>]
```

`<job-directory>` is the input-photo directory used for the job.

If no directory is supplied, the current working directory is used.

Examples:

```bash
archivetool status
```

```bash
archivetool status ./photos
```

## `finalise`

Exports the completed job to CSV from the current working directory.

```bash
archivetool finalise -o <output.csv>
```

Options:

- `-o, --output <output.csv>`  
  CSV output path. Required.

Example:

```bash
archivetool finalise -o ./result.csv
```

`finalise` does not take a job-directory argument. Run it from the job's input-photo directory.

## Job Directories

The **job directory** is the directory containing the original input images.

For example:

```text
batch01/
├── IMG001.jpg
├── IMG002.jpg
└── .archivetool_generated/
    └── job.json
```

Use:

```text
batch01/
```

as the job directory.

Do **not** pass `.archivetool_generated/` or `job.json` as the job directory. ArchiveTool manages its internal job-state files automatically.

## Paths with Spaces

Wrap paths containing spaces in quotes.

Example:

```bash
archivetool run -i "C:\\Archive Photos\\Batch 01" -o "C:\\Archive Exports\\result.csv"
```

## Help

Show general help:

```bash
archivetool --help
```

Show help for a command:

```bash
archivetool run --help
archivetool preview --help
archivetool preview create --help
archivetool preview review --help
archivetool ocr --help
archivetool ocr create --help
archivetool ocr review --help
archivetool status --help
archivetool finalise --help
```

## Current Development Notes

The command-line interface is implemented, but some processing stages are still being integrated with the backend.

In particular:

- preview creation is still being completed in the Switchboard/Detection integration;
- status output is still under development;
- auto-approval behaviour may change while the review integration is finalised;
- the packaged `archivetool` launcher and installation instructions are not yet final.

The CLI syntax in this guide should be updated if the public Switchboard interface changes before release.
