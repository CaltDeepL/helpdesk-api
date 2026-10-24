package com.caltdeepl.helpdesk.common.time;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 現在時刻の取得元。テストで時刻を固定できるよう、{@code Instant.now()} を直接呼ばずにこの Clock を使う。 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
