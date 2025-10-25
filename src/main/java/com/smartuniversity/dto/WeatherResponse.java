package com.smartuniversity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class WeatherResponse {

    private CurrentWeather current;
    private List<HourlyForecast> hourly;
    private List<DailyForecast> daily;

    // Constructors
    public WeatherResponse() {}

    public WeatherResponse(CurrentWeather current, List<HourlyForecast> hourly, List<DailyForecast> daily) {
        this.current = current;
        this.hourly = hourly;
        this.daily = daily;
    }

    // Getters and Setters
    public CurrentWeather getCurrent() {
        return current;
    }

    public void setCurrent(CurrentWeather current) {
        this.current = current;
    }

    public List<HourlyForecast> getHourly() {
        return hourly;
    }

    public void setHourly(List<HourlyForecast> hourly) {
        this.hourly = hourly;
    }

    public List<DailyForecast> getDaily() {
        return daily;
    }

    public void setDaily(List<DailyForecast> daily) {
        this.daily = daily;
    }

    // Inner Classes
    public static class CurrentWeather {
        private int temperature;
        private String condition;
        private int humidity;
        private int windSpeed;
        private int feelsLike;
        private int pressure;
        private int visibility;
        private int uvIndex;
        private String sunrise;
        private String sunset;

        // Constructors
        public CurrentWeather() {}

        // Getters and Setters
        public int getTemperature() {
            return temperature;
        }

        public void setTemperature(int temperature) {
            this.temperature = temperature;
        }

        public String getCondition() {
            return condition;
        }

        public void setCondition(String condition) {
            this.condition = condition;
        }

        public int getHumidity() {
            return humidity;
        }

        public void setHumidity(int humidity) {
            this.humidity = humidity;
        }

        public int getWindSpeed() {
            return windSpeed;
        }

        public void setWindSpeed(int windSpeed) {
            this.windSpeed = windSpeed;
        }

        public int getFeelsLike() {
            return feelsLike;
        }

        public void setFeelsLike(int feelsLike) {
            this.feelsLike = feelsLike;
        }

        public int getPressure() {
            return pressure;
        }

        public void setPressure(int pressure) {
            this.pressure = pressure;
        }

        public int getVisibility() {
            return visibility;
        }

        public void setVisibility(int visibility) {
            this.visibility = visibility;
        }

        public int getUvIndex() {
            return uvIndex;
        }

        public void setUvIndex(int uvIndex) {
            this.uvIndex = uvIndex;
        }

        public String getSunrise() {
            return sunrise;
        }

        public void setSunrise(String sunrise) {
            this.sunrise = sunrise;
        }

        public String getSunset() {
            return sunset;
        }

        public void setSunset(String sunset) {
            this.sunset = sunset;
        }
    }

    public static class HourlyForecast {
        private String time;
        private int temperature;
        private String condition;
        private int precipitation;

        // Constructors
        public HourlyForecast() {}

        // Getters and Setters
        public String getTime() {
            return time;
        }

        public void setTime(String time) {
            this.time = time;
        }

        public int getTemperature() {
            return temperature;
        }

        public void setTemperature(int temperature) {
            this.temperature = temperature;
        }

        public String getCondition() {
            return condition;
        }

        public void setCondition(String condition) {
            this.condition = condition;
        }

        public int getPrecipitation() {
            return precipitation;
        }

        public void setPrecipitation(int precipitation) {
            this.precipitation = precipitation;
        }
    }

    public static class DailyForecast {
        private String day;
        private int high;
        private int low;
        private String condition;
        private int precipitation;

        // Constructors
        public DailyForecast() {}

        // Getters and Setters
        public String getDay() {
            return day;
        }

        public void setDay(String day) {
            this.day = day;
        }

        public int getHigh() {
            return high;
        }

        public void setHigh(int high) {
            this.high = high;
        }

        public int getLow() {
            return low;
        }

        public void setLow(int low) {
            this.low = low;
        }

        public String getCondition() {
            return condition;
        }

        public void setCondition(String condition) {
            this.condition = condition;
        }

        public int getPrecipitation() {
            return precipitation;
        }

        public void setPrecipitation(int precipitation) {
            this.precipitation = precipitation;
        }
    }
}
