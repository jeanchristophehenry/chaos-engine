package com.gemalto.chaos.notification.impl;

import com.gemalto.chaos.notification.ChaosEvent;
import com.gemalto.chaos.notification.NotificationMethods;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ConsoleNotification implements NotificationMethods {
    private static final Logger log = LoggerFactory.getLogger(ConsoleNotification.class);

    public ConsoleNotification () {
        log.debug("Creating console logger");
    }

    @Override
    public void logEvent (ChaosEvent event) {
        log.debug("{}", StructuredArguments.value("ChaosEvent", event));
    }
}

