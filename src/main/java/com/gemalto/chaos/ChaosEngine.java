package com.gemalto.chaos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ChaosEngine
{
    private static final Logger log = LoggerFactory.getLogger(ChaosEngine.class);
    public static void main( String[] args )
    {
        ApplicationContext context = SpringApplication.run(ChaosEngine.class);
    }
}
