package com.gemalto.chaos.platform.impl;

import com.gemalto.chaos.ChaosException;
import com.gemalto.chaos.container.Container;
import com.gemalto.chaos.container.ContainerManager;
import com.gemalto.chaos.container.impl.CloudFoundryContainer;
import com.gemalto.chaos.platform.Platform;
import com.gemalto.chaos.services.impl.CloudFoundryService;
import org.cloudfoundry.operations.DefaultCloudFoundryOperations;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnBean(CloudFoundryService.class)
public class CloudFoundryPlatform implements Platform {

    private static final Logger log = LoggerFactory.getLogger(CloudFoundryPlatform.class);

    @Autowired
    private DefaultCloudFoundryOperations cloudFoundryOperations;

    @Autowired
    private ContainerManager containerManager;

    @Override
    public void degrade(Container container) {
        if (!(container instanceof CloudFoundryContainer)) {
            throw new ChaosException("Expected to be passed a Cloud Foundry container");
        }
        log.info("Attempting to degrade performance on {}", container);

    }

    @Override
    public List<Container> getRoster() {
        List<Container> containers = new ArrayList<>();
        Flux<ApplicationSummary> apps = cloudFoundryOperations.applications().list();
        for (ApplicationSummary app : apps.toIterable()) {
            Integer instances = app.getInstances();
            for (Integer i = 0; i < instances; i++) {
                CloudFoundryContainer c = CloudFoundryContainer
                        .builder()
                        .applicationId(app.getId())
                        .name(app.getName())
                        .instance(i)
                        .build();
                Container persistentContainer = containerManager.getOrCreatePersistentContainer(c);
                containers.add(persistentContainer);
                if (persistentContainer == c) {
                    log.info("Added container {}", persistentContainer);
                } else {
                    log.info("Existing container found: {}", persistentContainer);
                }
            }
        }
        return containers;
    }

    @Override
    public void destroy(Container container) {
        if (!(container instanceof CloudFoundryContainer)) {
            throw new ChaosException("Expected to be passed a Cloud Foundry container");
        }

        cloudFoundryOperations.applications().restartInstance(
                ((CloudFoundryContainer) container).getRestartApplicationInstanceRequest());


    }

}
