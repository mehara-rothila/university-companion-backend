package com.smartuniversity.service;

import com.smartuniversity.dto.WeatherResponse;
import com.smartuniversity.dto.WeatherResponse.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    @Value("${weather.api.key}")
    private String apiKey;

    private static final String BASE_URL = "https://api.openweathermap.org/data/2.5";
    private static final double UOM_LAT = 6.7951276;
    private static final double UOM_LON = 79.900867;
    private static final ZoneId SRI_LANKA_ZONE = ZoneId.of("Asia/Colombo");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WeatherService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Cacheable(value = "weatherCache", key = "'uom-weather'")
    public WeatherResponse getWeather() {
        try {
            CurrentWeather current = fetchCurrentWeather();
            JsonNode forecastData = fetchForecast();
            List<HourlyForecast> hourly = parseHourlyForecast(forecastData);
            List<DailyForecast> daily = parseDailyForecast(forecastData);

            return new WeatherResponse(current, hourly, daily);
        } catch (Exception e) {
            log.error("Failed to fetch weather data", e);
            throw new RuntimeException("Failed to fetch weather data: " + e.getMessage());
        }
    }

    private CurrentWeather fetchCurrentWeather() {
        String url = String.format("%s/weather?lat=%f&lon=%f&appid=%s&units=metric",
                BASE_URL, UOM_LAT, UOM_LON, apiKey);

        String response = restTemplate.getForObject(url, String.class);

        try {
            JsonNode root = objectMapper.readTree(response);
            CurrentWeather weather = new CurrentWeather();

            weather.setTemperature((int) Math.round(root.path("main").path("temp").asDouble()));
            weather.setCondition(getConditionSafe(root.path("weather")));
            weather.setHumidity(root.path("main").path("humidity").asInt());
            weather.setWindSpeed((int) Math.round(root.path("wind").path("speed").asDouble() * 3.6)); // m/s to km/h
            weather.setFeelsLike((int) Math.round(root.path("main").path("feels_like").asDouble()));
            weather.setPressure(root.path("main").path("pressure").asInt());
            weather.setVisibility((int) Math.round(root.path("visibility").asDouble() / 1000)); // meters to km
            weather.setUvIndex(-1); // UV data unavailable on free tier
            weather.setSunrise(formatTime(root.path("sys").path("sunrise").asLong()));
            weather.setSunset(formatTime(root.path("sys").path("sunset").asLong()));

            return weather;
        } catch (Exception e) {
            log.error("Failed to parse current weather", e);
            throw new RuntimeException("Failed to parse current weather: " + e.getMessage());
        }
    }

    private JsonNode fetchForecast() {
        String url = String.format("%s/forecast?lat=%f&lon=%f&appid=%s&units=metric",
                BASE_URL, UOM_LAT, UOM_LON, apiKey);

        String response = restTemplate.getForObject(url, String.class);

        try {
            return objectMapper.readTree(response);
        } catch (Exception e) {
            log.error("Failed to parse forecast data", e);
            throw new RuntimeException("Failed to parse forecast data: " + e.getMessage());
        }
    }

    private List<HourlyForecast> parseHourlyForecast(JsonNode forecastData) {
        List<HourlyForecast> hourlyList = new ArrayList<>();
        JsonNode list = forecastData.path("list");

        for (int i = 0; i < Math.min(6, list.size()); i++) {
            JsonNode item = list.get(i);
            HourlyForecast hourly = new HourlyForecast();

            hourly.setTime(formatTime(item.path("dt").asLong()));
            hourly.setTemperature((int) Math.round(item.path("main").path("temp").asDouble()));
            hourly.setCondition(getConditionSafe(item.path("weather")));
            hourly.setPrecipitation((int) Math.round(item.path("pop").asDouble() * 100));

            hourlyList.add(hourly);
        }

        return hourlyList;
    }

    private List<DailyForecast> parseDailyForecast(JsonNode forecastData) {
        List<DailyForecast> dailyList = new ArrayList<>();
        Map<String, List<JsonNode>> dailyMap = new java.util.LinkedHashMap<>();

        JsonNode list = forecastData.path("list");

        // Group by day using Sri Lanka timezone
        for (JsonNode item : list) {
            long timestamp = item.path("dt").asLong();
            LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(timestamp),
                SRI_LANKA_ZONE
            );
            String dateKey = dateTime.toLocalDate().toString();

            dailyMap.computeIfAbsent(dateKey, k -> new ArrayList<>()).add(item);
        }

        // Convert to daily forecasts
        dailyMap.entrySet().stream()
            .limit(7)
            .forEach(entry -> {
                List<JsonNode> items = entry.getValue();
                if (items.isEmpty()) return;

                DailyForecast daily = new DailyForecast();

                long timestamp = items.get(0).path("dt").asLong();
                daily.setDay(getDayName(timestamp));

                double high = items.stream()
                    .mapToDouble(item -> item.path("main").path("temp").asDouble())
                    .max().orElse(0);
                double low = items.stream()
                    .mapToDouble(item -> item.path("main").path("temp").asDouble())
                    .min().orElse(0);

                daily.setHigh((int) Math.round(high));
                daily.setLow((int) Math.round(low));

                daily.setCondition(getConditionSafe(items.get(0).path("weather")));

                double avgPrecip = items.stream()
                    .mapToDouble(item -> item.path("pop").asDouble())
                    .average().orElse(0);
                daily.setPrecipitation((int) Math.round(avgPrecip * 100));

                dailyList.add(daily);
            });

        return dailyList;
    }

    /**
     * Safely extract weather condition from the "weather" JSON array.
     * Returns "Unknown" if array is null or empty.
     */
    private String getConditionSafe(JsonNode weatherArray) {
        if (weatherArray != null && weatherArray.isArray() && weatherArray.size() > 0) {
            return weatherArray.get(0).path("main").asText("Unknown");
        }
        return "Unknown";
    }

    private String formatTime(long timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochSecond(timestamp),
            SRI_LANKA_ZONE
        );
        return dateTime.format(DateTimeFormatter.ofPattern("h:mm a"));
    }

    private String getDayName(long timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochSecond(timestamp),
            SRI_LANKA_ZONE
        );
        return dateTime.format(DateTimeFormatter.ofPattern("EEEE"));
    }
}
