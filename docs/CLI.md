# ArchiveTool CLI — Usage and Demo Guide

ArchiveTool provides a command-line interface (CLI) built with [**Clikt**](https://ajalt.github.io/clikt/).
Clikt has some quirks relating to advanced usage[^1]

It parses commands, validates options and delegates archive processing to an in internal mechanism called the '**Switchboard**'.   
The Switchboard acts as a backend, implementing the program's main functionality, so the CLI does not have to duplicate any significant work.

## Command reference

| Command                      | Options                                     | Behaviour                                                                                                                                                                                                                                                                                                     |
|------------------------------|---------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `info`                       | None                                        | Prints out program and system-global information. <br />Specifically: program version, copyright and licensing details, and the location of the configuration file.                                                                                                                                           |
| `status`                     | `[-i DIR]`                                  | Prints out job-specific information for a given directory, such as processing progress.                                                                                                                                                                                                                       |
| `run`                        | `[-i DIR] -o FILE [--no-resume] [-A 0-100]` | Run all six processing stages in sequence                                                                                                                                                                                                                                                                     |
| `preview create`             | `[-i DIR] [--no-resume]`                    | Automatically segments every image in `DIR` into individual boxes and stickers.                                                                                                                                                                                                                               |
| `preview review`             | `[-i DIR] [-A 0-100]`                       | Allows the user to manually adjust the automatic segmentations created in the previous step. <br/>For more information on using this tool, see [TODO](./SegmentationReview.md).                                                                                                                               |
| `ocr create`                 | `[-i DIR]`                                  | Crops and applies legibility filters to the results of segmentation, then runs them through an LLM for transcription.                                                                                                                                                                                         |
| `ocr review`                 | `[-i DIR]`                                  | Allows the user to edit the transcriptions created by the previous step. <br/>Unlike edits to the resultant CSV, these changes are persisted between exports. <br/>However, the editor is not as intuitive as a spreadsheet editor. <br/>For more information on using this tool, see [TODO](./OCRReview.md). |
| `finalise` (_or `finalize`_) | `[-i DIR] -o FILE`                          | `generateExport` **only**                                                                                                                                                                                                                                                                                     |

### Input Directory (`-i, --input-directory`)
This option selects the folder containing the source images and any persisted state.  
It is optional for the commands above, with the default being the current working directory.  
The supplied directory must exist and be readable.  
_Do not pass the hidden `.archivetool_generated` to this argument._

### Output File (`-o, --output`)
This option is required for `run` and `finalise`, and specifies the location to write the destination CSV file to.  
If a file already exists in this location, it will be overwritten.

### Logging
The root command allows you to adjust its logging verbosity by putting these root options before any subcommands.

You can use either `-v`/`--verbose` or `-q`/`--quiet` to adjust the verbosity of logs to the console[^2].   

Passing `-L, --log-file PATH` will make archivetool additionally write logs to a file.  
Using `-V` or `-Q` will set the log verbosity for the file specified by `-L` separately.  
Existing log files are overwritten when the first log entry is written, and the parent directory must already exist.  

You can repeat any of `v`, `V`, `q`, or `Q` multiple times to make logging quieter or more verbose.

The below example only prints warnings and errors to the console, but records everything (including tracing notices) to a file called `log.txt`.
```shell
archivetool -q -L log.txt -VV status -i "./Archive Photos/Batch 01"
```
### Resumption
`preview create` and `run` will _resume_ by default.
If you pass `--no-resume`, they will only work on folders where no existing state exists.

Otherwise, any command can be called a second time to pick up whereever you left off, 
since they save the most recent edits automatically, either per-image or in chunks.

### Automatic Approval
For `run` and `preview review`, `-A, --auto-approve-threshold` accepts an integer percentage between 0 and 100.  
Any files that are segmented with a minimum 'confidence' over this percentage 
will not be sent to the `preview review` GUI and will be automatically marked as approved for OCR. 

### State
`preview review`, `ocr create`, `ocr review`, and `finalise` are stateful.  
These commands depend on the state generated by the prior commands in the pipeline, 
and will automatically fail if the preceding step was not run beforehand.  
Additionally, `status` can be used to inspect the state of a given input directory. 
However, it will not fail with an empty directory.

### Progress
Many commands have a progress bar drawn to the terminal.  
These commands may take a long time, even per progress step, and may be quiet when logging. These steps are running, as long as the little spinner keeps spinning.  
Each time the bar progresses is a "snapshot" that you can resume from by calling the same command again.  

## Running in a Single Command Call

```shell
archivetool run -i "./Archive Photos/" -o "./Archive Exports/result.csv"
```

The `run` command is functionally equivalent to calling the following series of commands in order:
- `preview create`
- `preview review`
- `ocr create`
- `ocr review`
- `finalise`

[^1]: Most notably, the arguments to a subcommand must come after said sub-command and before the next one.
[^2]: Specifically, `stderr`.
