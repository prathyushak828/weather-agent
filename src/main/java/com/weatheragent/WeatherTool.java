package com.weatheragent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class WeatherTool {

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static String getWeather(String city) {
        try {
            // Step 1: Geocode the city name to lat/lon
            String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name="
                    + city.replace(" ", "+") + "&count=1";

            String geoResponse = httpGet(geoUrl);
            JsonNode geoJson = mapper.readTree(geoResponse);

            if (!geoJson.has("results") || geoJson.get("results").isEmpty()) {
                return "Error: City not found: " + city;
            }

            JsonNode location = geoJson.get("results").get(0);
            double lat = location.get("latitude").asDouble();
            double lon = location.get("longitude").asDouble();
            String cityName = location.get("name").asText();
            String country = location.get("country").asText();

            // Step 2: Fetch weather from Open-Meteo
            String weatherUrl = String.format(
                "https://api.open-meteo.com/v1/forecast" +
                "?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m," +
                "wind_speed_10m,weather_code" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,weather_code" +
                "&forecast_days=5&timezone=auto",
                lat, lon
            );

            String weatherResponse = httpGet(weatherUrl);
            JsonNode weatherJson = mapper.readTree(weatherResponse);

            JsonNode current = weatherJson.get("current");
            JsonNode daily = weatherJson.get("daily");

            double temp = current.get("temperature_2m").asDouble();
            double feelsLike = current.get("apparent_temperature").asDouble();
            int humidity = current.get("relative_humidity_2m").asInt();
            double windSpeed = current.get("wind_speed_10m").asDouble();
            int weatherCode = current.get("weather_code").asInt();
            String condition = describeWeatherCode(weatherCode);

            // Build forecast summary
            StringBuilder forecast = new StringBuilder();
            JsonNode dates = daily.get("time");
            JsonNode maxTemps = daily.get("temperature_2m_max");
            JsonNode minTemps = daily.get("temperature_2m_min");
            JsonNode precip = daily.get("precipitation_sum");
            JsonNode dailyCodes = daily.get("weather_code");

            for (int i = 0; i < Math.min(5, dates.size()); i++) {
                forecast.append(String.format("\n  %s: %s, high %.0f°C / low %.0f°C, rain %.1fmm",
                    dates.get(i).asText(),
                    describeWeatherCode(dailyCodes.get(i).asInt()),
                    maxTemps.get(i).asDouble(),
                    minTemps.get(i).asDouble(),
                    precip.get(i).asDouble()
                ));
            }

            return String.format(
                "Weather in %s, %s:\n" +
                "  Condition: %s\n" +
                "  Temperature: %.1f°C (feels like %.1f°C)\n" +
                "  Humidity: %d%%\n" +
                "  Wind: %.1f km/h\n" +
                "5-day forecast:%s",
                cityName, country, condition, temp, feelsLike,
                humidity, windSpeed, forecast.toString()
            );

        } catch (Exception e) {
            return "Error fetching weather: " + e.getMessage();
        }
    }

    private static String httpGet(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private static String describeWeatherCode(int code) {
        if (code == 0)        return "clear sky";
        if (code <= 3)        return "partly cloudy";
        if (code <= 9)        return "foggy";
        if (code <= 19)       return "drizzle";
        if (code <= 29)       return "rain";
        if (code <= 39)       return "snow";
        if (code <= 49)       return "freezing fog";
        if (code <= 59)       return "drizzle";
        if (code <= 69)       return "rain";
        if (code <= 79)       return "snow";
        if (code <= 89)       return "thunderstorm";
        return "heavy thunderstorm";
    }
}