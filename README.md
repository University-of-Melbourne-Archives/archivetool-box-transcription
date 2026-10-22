# archivetool
`archivetool` is mass-digitisation software, designed to expedite the detection, transcription and recording of a 
large physical archive.

The easiest way to get started is to download the prebuilt JAR and script from GitHub Releases, and then see [**Running**](#running).

For instructions on compiling from source, see [**Compilation**](#customisation).

It is customisable to suit a large variety of collections and requirements, 
though the base configuration is specifically suited to the University of Melbourne Archives.  
The base settings, and instructions on how to customise them to suit your needs are available under [**Customisation**](#customisation).

This project may require additional customisation if you need to process confidential information.  
More information is available under [**Internet Connectivity and Confidentiality**](#internet-connectivity-and-confidentiality) 

If you want to distribute this project yourself, see [**Licensing and Credits**](#licensing-and-credits) for more information.

Other documentation that may be useful if you wish to contribute to further development is available under [`docs/`](docs/).

## Running
`archivetool` is distributed as a `.jar` file, with an accompanying launcher script.

In order to run `archivetool`, you will need:
- A copy of the software.  
  You will need to extract the `zip` or `tar` somewhere, and note down the path to `bin/archivetool.bat` script on Windows, and the `bin/archivetool` script on macOS or Linux.[^2]
- A Java Runtime that supports at least Java 21.  
_You likely already have this installed, or you can download it for your platform at [getjdk.com](https://getjdk.com/?version=21&os=any&arch=any&distribution=oracle&package=any>)._
- An OpenAI-compatible language model that supports the Chat-Completions endpoint and Vision.  
  You can use either:
  - An API Key to a cloud model, such as ChatGPT, Claude, Microsoft Copilot or Google Gemini.
  - Alternatively, if your hardware is powerful enough, you can host a model locally using [Ollama](https://ollama.com/) or similar.  
  Both options have pros and cons[^1].  
<!-- TODO There might be other dependencies I missed here... -->

Next, you need to go out and take a series of pictures, of the items you wish to digitise.
Ideally, these pictures are front-on and as clear as possible, but we appreciate that that may not always be possible.
There can be multiple boxes per picture. You can a series of sample images in [our test resources](lib/src/test/resources/SwitchboardTest).
There are no file-name or -format restrictions (within reason), or resolution requirements (although, higher resolutions may improve legibility).

Place these images inside a folder on the system you plan to run `archivetool` on. You will need the path to this folder.

Then you will need to open a terminal in the folder you just created:
- On Windows, click in the address par at the top of File Explorer, and type `cmd`. 
- On macOS, follow [these instructions](https://ladedu.com/how-to-open-a-terminal-window-at-any-folder-from-finder-in-macos/).
- On Linux, use `cd` or look up how to do this with your file manager.

Next, you will need to set up the LLM you plan to use.
- If you wish to put your API key in an environment variable, use `ARCHIVETOOL_API_KEY`, 
  by writing `ARCHIVETOOL_API_KEY="<YOUR_KEY>" /path/to/archivetool` when running the scripts below.
  This will be prioritised over whatever is in the file below.
- Then, if you are using a different model, or if you want your API key to be persisted in a file, 
  run `archivetool preview create` and then stop it with <kbd>Ctrl</kbd> + <kbd>C</kbd>.  
  This will create a settings file, where you can change what model, base URL and api key is used.  
  You can edit this file using Notepad, TextEdit or your favorite code editor.
  Remember to save your changes before running `archivetool`. 
  You can see [**Customisation: OCR**](#ocr) for more details on how you can change this.
  The exact location depends on platform and system configuration[^3], but it is usually at:
  - `%APPDATA%/archivetool/config.json` (i.e. `C:/Users/<YOUR_NAME>/AppData/Roaming/archivetool/config.json`) on Windows
  - `~/Library/Application Support/archivetool/config.json` on macOS
  - and `$XDG_CONFIG_HOME/archivetool/config.json` (i.e. `~/.config/archivetool/config.json`) on Linux and other platforms.       
  


Finally, you will need to decide on where you want the manifest to be output (as a CSV file).
If you're unsure, `./manifest.csv` will create a file called `manifest.csv` alongside the folder of photos.

Then, run the `archivetool` command-line interface.  
You will need the path to where you extracted the `zip` or `tar`, and the location you want the CSV to output.
On macOS/Linux, run: 
```shell
/path/to/extracted/folder/bin/archivetool run -o /path/to/save/generated.csv
```
or on Windows, run:
```shell
/path/to/extracted/folder/bin/archivetool.bat run -o /path/to/save/generated.csv
```
Replacing the placeholder paths as appropriate.

This will run all of the steps sequentially, expecting user interaction when required.
In particular, it will run the following:
1. Box Segmentation
2. Box Segmentation Review (using a GUI, requires manual review)
3. Image Manipulation (to improve OCR results)
4. OCR Transcription
5. OCR Review (using a GUI, requires manual review)
6. CSV Export

Alternatively, you can run the individual steps separately 
or pass additional options, as described in [`docs/CLI.md`](docs/CLI.md) or by running `/path/to/archivetool --help`.

## Building
This project uses [Gradle](https://gradle.org/).
To build and run the application, use the *Gradle* tool window by running the wrapper directly from the terminal.
This will automatically download Gradle 9.3.0 upon first run.

Additionally, if you wish to build the Python module, you will need to enable it as described in 
[**Python Implementations**](#python-implementations), you will also need to install the Python [Black](https://pypi.org/project/black/) autoformatter.

- Run `./gradlew fmt` to run the autoformatter.
- Run `./gradlew run` to build and run the application.
- Run `./gradlew build` to only build the application.
- Run `./gradlew check` to run all checks, including tests.
- Run `./gradlew clean` to clean all build outputs.
- You can install `archivetool` to your system with `./gradlew installDist`.

Finally, after `./gradlew build`, the distribution archives (`.zip` and `.tar`) are available in [`app/build/distributions`](app/build/distributions).

This project is multi-module with the CLI living in `app`, and the core OCR library living in `lib`, and Python code in `python`.
As much of possible of the shared build logic is extracted to a convention plugin located in `buildSrc`.

This project uses a version catalog (see `gradle/libs.versions.toml`) to declare and version dependencies, wherever possible.

### Only Bundling Platform-Specific Native JARs

If you are only building for a single system, you may want to set the `archivetool.platform` to `<platform>-<arch>` 
in order to only bundle binaries for your platform into the JAR. This will save a little bit on space and load times but 
will mean that the program will crash at runtime on an unsupported platform.
Additionally, you can add a `-gpu` suffix to enable some CUDA support, if you have an Nvidia GPU.
Alternatively, you can set this value to `host`, and archivetool will detect platform based on the system you are compiling on.

You can set this value on the command-line with `-Parchivetool.platform=linux-x86_64` or by creating a file called 
`local.properties` next to the `gradle.properties`, with the contents:
```properties
archivetool.platform=linux-x86_64
```

Leaving the property empty or as an unsupported value will simply import every platform.

### Python Implementations
When we were experimenting with our Machine Learning models, they were scripted using Python.
This worked using GraalPy and a lot of interoperation code.

However, some of the packages required patches and fresh compilation, which was untenable for us during development.
Additionally, we have reimplemented the code in Kotlin, so it is redundant.

If you wish to compile the interoperation code, along with the original Python implementations of certain interactions, 
set `archivetool.python` to `true` in [`gradle.properties`](gradle.properties).

This does not replace the Kotlin implementations by default, 
and `Switchboard` will still use the Kotlin implementations unless you tell it otherwise. 

## Customisation
### Box Segmentation
`archivetool` uses an ONNX export of an incredibly fine-tuned  version of Ultralytics' YOLO 26 Nano.
It is incredibly quick, and very, very good at detecting brown cardboard archive boxes and white sticker labels.
However, if your items look at all different, it will struggle to detect them, if it can at all.
<!--TODO This isnt out yet--> 
There is a training harness available that allows you to train on hand-annotated images from your own collection.
You can find out more about that in its documentation.

There is no command-line tool to swap it out yet, 
but you can replace the model at compile time by putting it along-side the existing model in [weights](lib/src/main/resources/model-sandbox/weights),
alongside the existing model, and changing the default path in [`BoxSegmentation`](lib/src/main/kotlin/segmentation/BoxSegmentation.kt).
### OCR
The OCR engine used in `archivetool` is based on a vision-LLM. We would have preferred a fully local solution, 
but the quality of the results when using a library like Kraken was unacceptably poor. 
In comparison, most vision models do a surprisingly good job.
If you prefer to try something completely local, 
you will need to write your own DIed implementation as described in [**Alternate Implementations**](#alternate-implementations).  

You can change where the API endpoints are that `archivetool` calls out to at runtime, via `config.json`, which is in the config path[^3].
It should support a wide range of OpenAI-compatible APIs. 

### Specific Models
Prism-ML Ternary Bonsai 2 was used locally during development to save on costs and time.  
This requires a [custom version of llama.cpp](https://github.com/PrismML-Eng/llama.cpp).
The model was hosted with the following command:
```shell
llama-server -hf prism-ml/Ternary-Bonsai-2-27B-gguf:Q2_0 --host 0.0.0.0 --port 5354 --api-key "LOLNOPE" -ngl 999999 -fa auto --image-min-tokens 1024 --temperature 0.7  --top_p 0.80 --top_k 20 --min_p 0.0 --presence-penalty 1.5 --repeat-penalty 1.0 -ctk q4_0 -ctv q4_0 --ctx-size 51200 --reasoning-preserve
```
and the following configuration:
```json
{
    "ocr_api": {
        "base_url": "http://localhost:5354/v1/",
        "api_key": "LOLNOPE",
        "model": "prism-ml/Ternary-Bonsai-2-27B-gguf:Q2_0",
        "effort": "none"
    }
}
```
On one of the developers' AMD Radeon 9700XT, using AMD HIP, this model ran about twice as fast as the default `gemini-flash-3-preview`, without any notable degradation of results.

#### Prompt
Additionally, the prompt can be customised in [OCRService.java](lib/src/main/java/au/edu/unimelb/archivetool/ocr/OCRService.java),
although this file may be moved to a Kotlin implementation that calls the Java library in the future.
The current prompt is a solid baseline, that leaves a good token-to-performance ratio, 
but it struggles with domain-specifics, 
which may be improved with more context in the prompt.  
For example, the name of the student publication _Farrago_ tends to really trip models up, 
but if they knew they were looking at UoM records, they may have more success.  

### Export Format
The Export step defaults to using a CSV generator called [`UoMCSV`](lib/src/main/kotlin/switchboard/interactions/export/UoMCSV.kt).  
This uses `Field.builder()` to create CSV fields from the context of a specific box in the correct shape for the underlying generator.  
It uses a shape designed to be easy to import into the Drupal-shaped CMS used at the University of Melbourne.

You can implement your own format if you need something different. 
If you still need a CSV, you can use the same `Field.builder()` function.  
Each field is a pair of its header string, and a lambda to format an arbitrary box correctly.
You do not need to worry about escaping characters or anything, the generator will do that for you.
To use this implementation, simply pass it into the `impl` field of the appropriate CSV interaction in the CLI.  
If you need a fully different format, 
you will need to write your own DIed implementation as described in [**Alternate Implementations**](#alternate-implementations)

### Alternate Implementations
`Switchboard` is a very flexible tool, 
that allows you to dependency-inject any custom implementation of an interaction that you would like, 
as long as it implements the appropriate interface. 

This means that if you need support for something dramatically different, you can plug it in using `Switchboard.with()`, 
and not change any internal logic.

An example of doing this can be seen in [`PythonSwitchboard`](python/src/main/kotlin/PythonSwitchboard.kt).
## Larger Structure
## Internet Connectivity and Confidentiality
By default, `archivetool` connects to the internet to use a cloud language model, specifically Google Gemini.

If you are under obligations to keep records confidential or otherwise don't trust an external model, 
see [**Customisation: OCR**](#ocr) 
## Licensing and Credits

Copyright &copy; 2026 The University of Melbourne

`archivetool` was originally built for [COMP30022: IT Project](https://handbook.unimelb.edu.au/2026/subjects/comp30022/assessment)
by @91woshinailong, @jtee27, @ledwardes, @Siri-chan and @Ulrich-Fuhring.

This program is free software: you can redistribute it and/or modify it under the terms of the 
GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, 
or (at your option) any later version.  
This program is distributed in the hope that it will be useful, but **WITHOUT ANY WARRANTY**;
without even the implied warranty of **MERCHANTABILITY** or **FITNESS FOR A PARTICULAR PURPOSE**.  
See the GNU Affero General Public License, provided in [LICENSE](LICENSE), or at 
[`<https://www.gnu.org/licenses/>`](https://www.gnu.org/licenses/) for more details.


[^1]: `archivetool` uses 1 request per segment, and so over a large collection processing may get expensive, or result in rate limiting.  
Additionally, the software currently needs to be restarted manually when it hits a rate-limit, so a free or cheap API key may not be suitable.  
On the other hand, a local model may be slower, hardware-intensive, and produce poorer results.

[^2]: Or to add it to `$PATH`, if you are so inclined.

[^3]: The specifics are described in [`Platform`](lib/src/main/kotlin/util/os/Platform.kt)


ONE MORE TODO BEFORE IF FORGET, MAKE SURE THE CLI OFFERS SOURCE CODE SOMEWHERE.