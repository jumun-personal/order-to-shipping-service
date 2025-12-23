package com.jumunhasyeotjo.order_to_shipping.coupon.infrastructure.config;


import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IssueCouponRedissonConfig {

    @Value("${spring.data.redis.cache.host}")
    private String host;

    @Value("${spring.data.redis.cache.port}")
    private int port;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
            .setAddress("redis://" + host + ":" + port);

        return Redisson.create(config);
    }
}
