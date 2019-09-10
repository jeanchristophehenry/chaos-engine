/*
 *    Copyright (c) 2019 Thales Group
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package com.thales.chaos.notification.services;

import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "datadog")
@ConditionalOnProperty(name = "datadog.enableEvents", havingValue = "true")
public class DataDogNotificationService {
    private int statsdPort = 8125;
    private String statsdHost = "datadog";
    private static final String[] STATIC_TAGS = { "service:chaosengine" };

    public void setStatsdPort (int statsdPort) {
        this.statsdPort = statsdPort;
    }

    public void setStatsdHost (String statsdHost) {
        this.statsdHost = statsdHost;
    }

    @Bean
    StatsDClient statsDClient(){
        return new NonBlockingStatsDClient("", statsdHost, statsdPort, STATIC_TAGS);
    }
}
