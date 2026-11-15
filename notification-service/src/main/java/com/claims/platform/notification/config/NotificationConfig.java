package com.claims.platform.notification.config;

import com.claims.platform.notification.producer.NotificationSender;
import com.claims.platform.notification.producer.impl.FakeNotificationSenders;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/** The clock, scheduling and the (fake) providers behind each channel. */
@Configuration
@EnableScheduling
public class NotificationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    NotificationSender emailSender() {
        return FakeNotificationSenders.email();
    }

    @Bean
    NotificationSender smsSender() {
        return FakeNotificationSenders.sms();
    }

    @Bean
    NotificationSender pushSender() {
        return FakeNotificationSenders.push();
    }
}
