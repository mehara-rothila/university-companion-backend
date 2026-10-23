package com.smartuniversity.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Arrays;

@Configuration
@EnableCaching
@EnableScheduling
public class WeatherConfig {

    @Bean
    public RestTemplate restTemplate() {
        // Stop waiting on a stalled Gemini or weather call: 10 seconds to connect, then a read
        // fails once no data has arrived for 60 seconds (an idle timeout, not a total deadline).
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(60));
        return new RestTemplate(requestFactory);
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(Arrays.asList(
                new ConcurrentMapCache("weatherCache")
        ));
        return cacheManager;
    }

    // Evict weather cache every 15 minutes to ensure fresh data
    @Scheduled(fixedRate = 900000)
    public void evictWeatherCache() {
        var cache = cacheManager().getCache("weatherCache");
        if (cache != null) {
            cache.clear();
        }
    }
}
