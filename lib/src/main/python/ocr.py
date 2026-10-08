import base64
import os
import re
from openai import OpenAI


def executeOCR(imagePath, base_url, api_key, model):
    """Perform OCR to read printed and handwritten texts from an image using Gemini model
    via OpenAI's library.

    Args:
        imagePath (str): Path of an image file.
        base_url (str): "Base URL" for the OpenAI API
        api_key (str): API key for the URL model.
        model (str): Model available at base_url that you want to use

    Returns:
        str: Extracted text from the image file.
    """
    # Encode image into Base64 string for the API
    with open(imagePath, "rb") as f:
        b64 = base64.b64encode(f.read()).decode("utf-8")

    client = OpenAI(api_key=api_key, base_url=base_url)

    response = client.chat.completions.create(
        model=model,
        messages=[
            {
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": (
                            "Extract all text from this image exactly as it appears. "
                            "Preserve line breaks and formatting. Do not summarize or add commentary. "
                            "Ignore strikethroughs, text on logo and randomly placed numbers outside the sticker."
                            "Identify the unique date ranges and output them as 'Date ranges: {date ranges}',"
                            "with the start and end of the range separated by a '!', and each date in ISO8601."
                            "If a single date appears, treat it as a range with no end"
                            "Return 'None' if there's none."
                        ),
                    },
                    {
                        "type": "image_url",
                        "image_url": {"url": f"data:image/jpeg;base64,{b64}"},
                    },
                ],
            },
        ],
        reasoning_effort="low",
    )

    return response.choices[0].message.content


def processOutputText(extractedText):
    """Take the extracted text and process it into a Python dict, where the text will be sorted into
    four keys:
        - Date range
        - Description
        - ID strings
        - Unit ID

    Args:
        extractedText (str): Extracted text from an image file.

    Returns:
        dict: Extracted text sorted into the aforementioned four keys.
    """
    # Initialise output dictionary and its keys
    outputDict = {}
    idStrings = {}
    unitId = ""
    description = []

    # Identify date range(s)
    dateRangePattern = r"Date ranges: [\w\W]+"
    dateRangeMatch = re.findall(dateRangePattern, extractedText)[0]
    outputDict["Date ranges"] = dateRangeMatch[len("Date ranges: ") :]
    # Remove date ranges to prevent dupes in description
    extractedText = re.sub(dateRangePattern, "", extractedText)
    extractedText = extractedText.replace(dateRangeMatch[len("Date ranges: ") :], "")

    # Identify format of ID string(s)
    # - {4 digit year}.{4 digit} ID string (e.g. 1964.0009)
    doubleFourPattern = r"\d{4}\.\d{4}"

    # - {2 digit year}/{unpadded number} ID string (e.g. 96/9, 79/29)
    twoDigitYearPattern = r"\d{2}\/\d+"

    # - New format ID string (e.g. UMA-AC-000000330 or UMA-SRE-20160038)
    umaPattern = r"(\bUMA-(?:AC|SR)E?-\d{8,9})"

    idStringPatterns = (doubleFourPattern, twoDigitYearPattern, umaPattern)
    for pattern in idStringPatterns:
        idStringMatches = re.findall(pattern, extractedText)
        if idStringMatches:
            # Use set to ensure ID string(s) are unique
            idStrings = set(idStringMatches)
            outputDict["ID strings"] = idStrings
            # Remove ID strings from the extracted text to prevent dupes in description
            if len(idStrings) > 1:
                for idString in idStrings:
                    extractedText = extractedText.replace(idString, "")
            elif len(idStrings) == 1:
                extractedText = extractedText.replace(list(idStrings)[0], "")
            break

    # Identify unit ID of a box
    unitIdPattern = r"\b(?:Unit|UNIT)\s\d+"
    unitIdMatch = re.findall(unitIdPattern, extractedText)
    if unitIdMatch:
        unitId = re.sub(r"\s+", " ", unitIdMatch[0])
        outputDict["Unit ID"] = unitId
        # Remove unit ID from extracted text to prevent dupes in description
        extractedText = re.sub(unitIdPattern, "", extractedText)

    # Add the rest of the text into the "Description" key of output_dict
    for line in extractedText.splitlines():
        line = line.strip()
        # Skip empty lines or lines that don't contain any alphanumeric
        if line and bool(re.match(r"[a-zA-Z0-9\s]+", line)):
            description.append(line)
    outputDict["Description"] = "; ".join(description)

    return outputDict
