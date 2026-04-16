package com.weatheragent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class WeatherAgent {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private final String apiKey;

    public WeatherAgent(String apiKey) {
        this.apiKey = apiKey;
    }

    public String chat(String userMessage) throws Exception {

        // Build conversation history as JSON array
        ArrayNode messages = mapper.createArrayNode();
        ObjectNode userMsg = mapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        // Define the get_weather tool
        ObjectNode cityProp = mapper.createObjectNode();
        cityProp.put("type", "string");
        cityProp.put("description", "The city name, e.g. Austin or Tokyo");

        ObjectNode properties = mapper.createObjectNode();
        properties.set("city", cityProp);

        ArrayNode required = mapper.createArrayNode();
        required.add("city");

        ObjectNode inputSchema = mapper.createObjectNode();
        inputSchema.put("type", "object");
        inputSchema.set("properties", properties);
        inputSchema.set("required", required);

        ObjectNode tool = mapper.createObjectNode();
        tool.put("name", "get_weather");
        tool.put("description", "Get current weather and 5-day forecast for any city.");
        tool.set("input_schema", inputSchema);

        ArrayNode tools = mapper.createArrayNode();
        tools.add(tool);

        // Agent loop
        while (true) {

            // Build request body
            ObjectNode requestBody = mapper.createObjectNode();
            requestBody.put("model", "claude-haiku-4-5-20251001");
            requestBody.put("max_tokens", 1024);
            requestBody.put("system",
                "You are a helpful weather assistant. Use the get_weather tool " +
                "whenever the user asks about weather. Give practical advice like " +
                "what to wear or whether to bring an umbrella.");
            requestBody.set("messages", messages);
            requestBody.set("tools", tools);

            // Call Anthropic API directly via HTTP
            String requestJson = mapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

            HttpResponse<String> httpResponse = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

            JsonNode response = mapper.readTree(httpResponse.body());

            // Check for API errors
            if (response.has("error")) {
                return "API Error: " + response.get("error").get("message").asText();
            }

            String stopReason = response.get("stop_reason").asText();
            JsonNode contentBlocks = response.get("content");

            // If done — extract and return text
            if (stopReason.equals("end_turn")) {
                StringBuilder result = new StringBuilder();
                for (JsonNode block : contentBlocks) {
                    if ("text".equals(block.get("type").asText())) {
                        result.append(block.get("text").asText());
                    }
                }
                return result.toString();
            }

            // If tool use — execute tool and feed result back
            if (stopReason.equals("tool_use")) {

                // Add assistant response to history
                ObjectNode assistantMsg = mapper.createObjectNode();
                assistantMsg.put("role", "assistant");
                assistantMsg.set("content", contentBlocks);
                messages.add(assistantMsg);

                // Find tool_use block and call the tool
                ArrayNode toolResults = mapper.createArrayNode();

                for (JsonNode block : contentBlocks) {
                    if ("tool_use".equals(block.get("type").asText())) {
                        String toolUseId = block.get("id").asText();
                        String city = block.get("input").get("city").asText();

                        System.out.println("[agent] calling get_weather for: " + city);
                        String weatherData = WeatherTool.getWeather(city);

                        ObjectNode toolResult = mapper.createObjectNode();
                        toolResult.put("type", "tool_result");
                        toolResult.put("tool_use_id", toolUseId);
                        toolResult.put("content", weatherData);
                        toolResults.add(toolResult);
                    }
                }

                // Add tool results to history
                ObjectNode toolResultMsg = mapper.createObjectNode();
                toolResultMsg.put("role", "user");
                toolResultMsg.set("content", toolResults);
                messages.add(toolResultMsg);
            }
        }
    }
}