package com.influxdb.app.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.influxdb.app.dto.MeasurementPointDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

@Repository
public class RedisRepository {

    private static final Logger logger = LoggerFactory.getLogger(RedisRepository.class);
    private static final String CACHE_PREFIX = "measurement:";
    private static final long CACHE_TTL = 1; // 1 hour

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Prosledi merenje u keš (Redis)
     */
    public void cacheMeasurement(MeasurementPointDTO measurement) {
        try {
            String key = CACHE_PREFIX + measurement.getId();
            String value = objectMapper.writeValueAsString(measurement);
            redisTemplate.opsForValue().set(key, value, CACHE_TTL, TimeUnit.HOURS);
            logger.info("Merenje keširano sa ključem: {}", key);
        } catch (Exception e) {
            logger.error("Greška pri kešovanju merenja", e);
        }
    }

    /**
     * Pronađi merenje iz keša
     */
    public MeasurementPointDTO getCachedMeasurement(String id) {
        try {
            String key = CACHE_PREFIX + id;
            Object cached = redisTemplate.opsForValue().get(key);

            if (cached != null) {
                logger.info("Merenje pronađeno u kešu za ključ: {}", key);
                return objectMapper.readValue(cached.toString(), MeasurementPointDTO.class);
            }
        } catch (Exception e) {
            logger.error("Greška pri čitanju iz keša", e);
        }
        return null;
    }

    /**
     * Obriši merenje iz keša
     */
    public void removeCachedMeasurement(String id) {
        try {
            String key = CACHE_PREFIX + id;
            redisTemplate.delete(key);
            logger.info("Merenje obrisano iz keša: {}", key);
        } catch (Exception e) {
            logger.error("Greška pri brisanju iz keša", e);
        }
    }

    /**
     * Prosledi brojač
     */
    public void incrementCounter(String counterName) {
        try {
            String key = "counter:" + counterName;
            redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, 24, TimeUnit.HOURS);
        } catch (Exception e) {
            logger.error("Greška pri inkrementiranju brojača", e);
        }
    }

    /**
     * Pronađi brojač
     */
    public long getCounter(String counterName) {
        try {
            String key = "counter:" + counterName;
            Object value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                return Long.parseLong(value.toString());
            }
        } catch (Exception e) {
            logger.error("Greška pri čitanju brojača", e);
        }
        return 0L;
    }
}
