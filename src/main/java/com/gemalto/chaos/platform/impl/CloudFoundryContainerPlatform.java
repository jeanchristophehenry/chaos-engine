package com.gemalto.chaos.platform.impl;

import com.gemalto.chaos.ChaosException;
import com.gemalto.chaos.constants.CloudFoundryConstants;
import com.gemalto.chaos.container.Container;
import com.gemalto.chaos.container.ContainerManager;
import com.gemalto.chaos.container.enums.ContainerHealth;
import com.gemalto.chaos.container.impl.CloudFoundryContainer;
import com.gemalto.chaos.platform.SshBasedExperiment;
import com.gemalto.chaos.platform.enums.CloudFoundryIgnoredClientExceptions;
import com.gemalto.chaos.ssh.SshCommandResult;
import com.gemalto.chaos.ssh.SshExperiment;
import com.gemalto.chaos.ssh.impl.CloudFoundrySshManager;
import com.gemalto.chaos.ssh.services.ShResourceService;
import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.client.v2.ClientV2Exception;
import org.cloudfoundry.client.v2.applications.ApplicationInstanceInfo;
import org.cloudfoundry.client.v2.applications.ApplicationInstancesRequest;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.cloudfoundry.operations.applications.RestartApplicationInstanceRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.gemalto.chaos.constants.CloudFoundryConstants.CLOUDFOUNDRY_APPLICATION_STARTED;
import static com.gemalto.chaos.constants.DataDogConstants.DATADOG_CONTAINER_KEY;
import static net.logstash.logback.argument.StructuredArguments.v;

@Component
@Primary
@ConditionalOnProperty({ "cf.containerChaos" })
@ConfigurationProperties("cf")
public class CloudFoundryContainerPlatform extends CloudFoundryPlatform implements SshBasedExperiment<CloudFoundryContainer> {
    @Autowired
    private CloudFoundryOperations cloudFoundryOperations;
    @Autowired
    private ContainerManager containerManager;
    @Autowired
    private CloudFoundryPlatformInfo cloudFoundryPlatformInfo;
    @Autowired
    private CloudFoundryClient cloudFoundryClient;
    @Autowired
    private ShResourceService shResourceService;

    @Autowired
    public CloudFoundryContainerPlatform () {
        log.info("PCF Container Platform created");
    }

    public ContainerHealth checkHealth (String applicationId, Integer instanceId) {
        Map<String, ApplicationInstanceInfo> applicationInstanceResponse;
        try {
            applicationInstanceResponse = cloudFoundryClient.applicationsV2()
                                                            .instances(ApplicationInstancesRequest.builder()
                                                                                                  .applicationId(applicationId)
                                                                                                  .build())
                                                            .block()
                                                            .getInstances();
        } catch (ClientV2Exception e) {
            if (CloudFoundryIgnoredClientExceptions.isIgnorable(e)) {
                log.warn("Platform returned ignorable exception: {} ", e.getMessage(), e);
                return ContainerHealth.RUNNING_EXPERIMENT;
            }
            log.error("Cannot get application instances: {}", e.getMessage(), e);
            return ContainerHealth.DOES_NOT_EXIST;
        }
        String status;
        try {
            status = applicationInstanceResponse.get(instanceId.toString()).getState();
        } catch (NullPointerException e) {
            return ContainerHealth.DOES_NOT_EXIST;
        }
        return (status.equals(CloudFoundryConstants.CLOUDFOUNDRY_RUNNING_STATE) ? ContainerHealth.NORMAL : ContainerHealth.RUNNING_EXPERIMENT);
    }

    @Override
    public List<Container> generateRoster () {
        return cloudFoundryOperations.applications()
                                     .list()
                                     .filter(app -> app.getRequestedState().equals(CLOUDFOUNDRY_APPLICATION_STARTED))
                                     .toStream(Integer.MAX_VALUE)
                                     .filter(applicationSummary -> {
                                         if (isChaosEngine(applicationSummary.getName())) {
                                             log.debug("Ignored what appears to be me: {}", v("ApplicationSummary", applicationSummary));
                                             return false;
                                         }
                                         return true;
                                     })
                                     .map(this::createContainersFromApplicationSummary)
                                     .flatMap(Collection::stream)
                                     .collect(Collectors.toList());
    }

    Collection<CloudFoundryContainer> createContainersFromApplicationSummary (ApplicationSummary applicationSummary) {
        Integer instances = applicationSummary.getInstances();
        return IntStream.range(0, instances)
                        .mapToObj(i -> createSingleContainerFromApplicationSummary(applicationSummary, i))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
    }

    CloudFoundryContainer createSingleContainerFromApplicationSummary (ApplicationSummary applicationSummary, Integer index) {
        CloudFoundryContainer container = containerManager.getMatchingContainer(CloudFoundryContainer.class, applicationSummary
                .getName() + "-" + index);
        if (container == null) {
            container = CloudFoundryContainer.builder()
                                             .applicationId(applicationSummary.getId())
                                             .name(applicationSummary.getName())
                                             .instance(index)
                                             .platform(this)
                                             .build();
            log.debug("Created Cloud Foundry Container {} from {}", v(DATADOG_CONTAINER_KEY, container), v("ApplicationSummary", applicationSummary));
            containerManager.offer(container);
        } else {
            log.debug("Found existing Cloud Foundry Container {}", v(DATADOG_CONTAINER_KEY, container));
        }
        return container;
    }

    public void restartInstance (RestartApplicationInstanceRequest restartApplicationInstanceRequest) {
        cloudFoundryOperations.applications().restartInstance(restartApplicationInstanceRequest).block();
    }

    public ContainerHealth sshBasedHealthCheckInverse (CloudFoundryContainer container, String command, int errorExitStatus) {
        CloudFoundrySshManager ssh = getSSHManager();
        try {
            ssh.connect(container);
            SshCommandResult result = ssh.executeCommand(command);
            if (errorExitStatus == result.getExitStatus()) {
                return ContainerHealth.RUNNING_EXPERIMENT;
            }
        } catch (IOException e) {
            log.warn("Unsuccessful ssh health check: {}", e.getMessage(), e);
            return ContainerHealth.RUNNING_EXPERIMENT;
        } finally {
            ssh.disconnect();
        }
        return ContainerHealth.NORMAL;
    }

    CloudFoundrySshManager getSSHManager () {
        return new CloudFoundrySshManager(getCloudFoundryPlatformInfo());
    }

    public ContainerHealth sshBasedHealthCheck (CloudFoundryContainer container, String command, int expectedExitStatus) {
        CloudFoundrySshManager ssh = getSSHManager();
        try {
            ssh.connect(container);
            SshCommandResult result = ssh.executeCommand(command);
            if (expectedExitStatus == result.getExitStatus()) {
                return ContainerHealth.NORMAL;
            }
        } catch (IOException e) {
            log.warn("Unsuccessful ssh health check: {}", e.getMessage(), e);
        } finally {
            ssh.disconnect();
        }
        return ContainerHealth.RUNNING_EXPERIMENT;
    }

    public void sshExperiment (SshExperiment sshExperiment, CloudFoundryContainer container) {
        CloudFoundrySshManager ssh = getSSHManager();
        try {
            sshExperiment.setSshManager(ssh);
            sshExperiment.setShResourceService(shResourceService);
            if (ssh.connect(container)) {
                if (container.getDetectedCapabilities() != null) {
                    sshExperiment.setDetectedShellSessionCapabilities(container.getDetectedCapabilities());
                }
                sshExperiment.runExperiment();
                if (container.getDetectedCapabilities() == null || container.getDetectedCapabilities() != sshExperiment.getDetectedShellSessionCapabilities()) {
                    container.setDetectedCapabilities(sshExperiment.getDetectedShellSessionCapabilities());
                }
            }
        } catch (IOException e) {
            throw new ChaosException(e);
        } finally {
            ssh.disconnect();
        }
    }
}
