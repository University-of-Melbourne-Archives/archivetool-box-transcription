import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ReasoningEffort;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionContentPartImage;
import com.openai.models.chat.completions.ChatCompletionContentPartText;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class OCRService {
    private static final String PROMPT =
            "Extract all text from this image exactly as it appears. "
                    + "Preserve line breaks and formatting. Do not summarize or add commentary. "
                    + "Ignore strikethrough, text on logo and randomly placed numbers outside the sticker. "
                    + "Identify the unique date ranges and output them as 'Date ranges: {date ranges}',"
                    + "with the start and end of the range separated by a '!', and each date in ISO8601. "
                    + "If a single date appears, treat it as a range with no end. "
                    + "Return 'None' if there's none.";
    private final OpenAIClient client;

    public OCRService(String apiKeyString) {
        this.client = OpenAIOkHttpClient.builder()
                .apiKey(apiKeyString)
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/openai/")
                .build();
    }

    public String executeOCR(String imagePathString) throws IOException {
        // Encode image into Base64 string for the API
        Path imagePath = Paths.get(imagePathString);
        byte[] imageBytes = Files.readAllBytes(imagePath);
        String base64String = Base64.getEncoder().encodeToString(imageBytes);
        String imageBase64Url = "data:image/png;base64," + base64String;

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model("gemini-3-flash-preview")
                .reasoningEffort(ReasoningEffort.LOW)
                .addUserMessageOfArrayOfContentParts(List.of(
                        ChatCompletionContentPart.ofText(
                                ChatCompletionContentPartText.builder()
                                        .text(PROMPT)
                                        .build()),
                        ChatCompletionContentPart.ofImageUrl(
                                ChatCompletionContentPartImage.builder()
                                        .imageUrl(ChatCompletionContentPartImage.ImageUrl.builder()
                                                .url(imageBase64Url)
                                                .build())
                                        .build())
                        )
                )
                .build();

        ChatCompletion response = client.chat().completions().create(params);
        return response.choices().getFirst().message().content().orElse("");
    }

    public HashMap<String, String> processOutputText(String extractedText) {
        HashMap<String, String> outputHashMap = new HashMap<>();

        // [STEP 1]: Identify date range(s)
        Pattern dateRangePattern = Pattern.compile("Date ranges: [\\w\\W]+");
        Matcher dateRangeMatcher = dateRangePattern.matcher(extractedText);
        if (dateRangeMatcher.find()) {
            // Only extract the actual date(s) after the "Date ranges: " string
            outputHashMap.put("Date ranges", dateRangeMatcher.group().substring("Date ranges: ".length()));
        } else {
            // Add "None" in case of LLM hallucination (i.e. not following the prompt's instruction)
            outputHashMap.put("Date ranges", "None");
        }
        // Remove date ranges to prevent dupes in the description
        extractedText = dateRangeMatcher.replaceAll("");

        // [STEP 2]: Identify format of ID string(s)
        // * {4 digit year}.{4 digit} ID string (e.g. 1964.0009)
        Pattern doubleFourPattern = Pattern.compile("\\d{4}\\.\\d{4}");
        // * {2 digit year}/{unpadded number} ID string (e.g. 96/9, 79/29)
        Pattern twoDigitYearPattern = Pattern.compile("\\d{2}/\\d+");
        // * New format ID string (e.g. UMA-AC-000000330 or UMA-SRE-20160038)
        Pattern umaPattern = Pattern.compile("\\bUMA-(?:AC|SR)E?-\\d{8,9}");

        List<Pattern> idStringPatterns = List.of(doubleFourPattern, twoDigitYearPattern, umaPattern);
        for (Pattern idStringPattern: idStringPatterns) {
            // Gather all ID strings in a list
            List<String> idStringMatches = idStringPattern.matcher(extractedText)
                    .results()
                    .map(MatchResult::group)
                    .toList();
            if (!idStringMatches.isEmpty()) {
                // Convert list to hashSet to ensure ID string(s) are unique
                HashSet<String> idStrings = new HashSet<>(idStringMatches);
                outputHashMap.put("ID strings", String.join(", ", idStrings));
                for (String idString: idStrings) {
                    extractedText = extractedText.replace(idString, "");
                }

                // Break because there can only be one format of ID string in each input image
                break;
            }
        }

        // [STEP 3]: Identify unit ID of a box
        Pattern unitIdPattern = Pattern.compile("\\b(?:Unit|UNIT)\\s\\d+");
        Matcher unitIdMatcher = unitIdPattern.matcher(extractedText);
        if (unitIdMatcher.find()) {
            String unitId = unitIdMatcher.group().replaceAll("\\s+", " ");
            outputHashMap.put("Unit ID", unitId);
            extractedText = extractedText.replace(unitId, "");
        } else {
            outputHashMap.put("Unit ID", "None");
        }

        // [STEP 4]: Add the rest of the text into the "Description" key of `outputHashMap`
        List<String> description = new ArrayList<>();
        for (String rawLine : extractedText.lines().toList()) {
            String line = rawLine.strip();
            Pattern alphaNumeric = Pattern.compile("[a-zA-Z0-9\\s]+");
            // Skip empty lines or lines that don't contain any alphanumeric
            if (!line.isEmpty() && alphaNumeric.matcher(line).lookingAt()) {
                description.add(line);
            }
        }
        outputHashMap.put("Description", String.join("; ", description));

        return outputHashMap;
    }
}
