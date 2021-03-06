/*
 *    Copyright (c) 2018 - 2020, Thales DIS CPL Canada, Inc
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

package com.thales.chaos.platform.impl;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.SetInstanceHealthRequest;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thales.chaos.constants.AwsConstants;
import com.thales.chaos.constants.AwsEC2Constants;
import com.thales.chaos.constants.DataDogConstants;
import com.thales.chaos.container.Container;
import com.thales.chaos.container.ContainerManager;
import com.thales.chaos.container.enums.ContainerHealth;
import com.thales.chaos.container.impl.AwsEC2Container;
import com.thales.chaos.exception.ChaosException;
import com.thales.chaos.platform.Platform;
import com.thales.chaos.platform.SshBasedExperiment;
import com.thales.chaos.platform.enums.ApiStatus;
import com.thales.chaos.platform.enums.PlatformHealth;
import com.thales.chaos.platform.enums.PlatformLevel;
import com.thales.chaos.selfawareness.AwsEC2SelfAwareness;
import com.thales.chaos.shellclient.ssh.SSHCredentials;
import com.thales.chaos.shellclient.ssh.impl.ChaosSSHCredentials;
import org.apache.commons.net.util.SubnetUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.thales.chaos.constants.AwsEC2Constants.*;
import static com.thales.chaos.constants.DataDogConstants.DATADOG_CONTAINER_KEY;
import static com.thales.chaos.exception.enums.AwsChaosErrorCode.*;
import static java.util.function.Predicate.not;
import static net.logstash.logback.argument.StructuredArguments.v;

@Component
@ConditionalOnProperty("aws.ec2")
@ConfigurationProperties("aws.ec2")
public class AwsEC2Platform extends Platform implements SshBasedExperiment<AwsEC2Container> {
    private final Map<String, String> vpcToSecurityGroupMap = new ConcurrentHashMap<>();
    private AmazonEC2 amazonEC2;
    private ContainerManager containerManager;
    private Map<String, List<String>> filter = new HashMap<>();
    private AwsEC2SelfAwareness awsEC2SelfAwareness;
    private List<String> groupingTags = Collections.singletonList(AWS_ASG_NAME_TAG_KEY);
    private Map<String, String> sshPrivateKeys = Collections.emptyMap();
    private Map<String, String> imageIdToUsernameMap = Collections.emptyMap();
    private Collection<SubnetUtils.SubnetInfo> routableCidrBlocks = Collections.emptySet();
    @Autowired
    private AmazonAutoScaling amazonAutoScaling;

    @Autowired
    AwsEC2Platform (AmazonEC2 amazonEC2, ContainerManager containerManager, AwsEC2SelfAwareness awsEC2SelfAwareness) {
        this();
        this.amazonEC2 = amazonEC2;
        this.containerManager = containerManager;
        this.awsEC2SelfAwareness = awsEC2SelfAwareness;
    }

    private AwsEC2Platform () {
        log.info("AWS EC2 Platform created");
    }

    Map<String, String> getVpcToSecurityGroupMap () {
        return new HashMap<>(vpcToSecurityGroupMap);
    }

    public void setFilter (@NotNull Map<String, List<String>> filter) {
        log.info("EC2 Instances will be filtered with the following tags and values: {}", v("filterCriteria", filter));
        this.filter = filter;
    }

    public void setGroupingTags (List<String> groupingTags) {
        log.info("EC2 Instances will consider designated survivors based on the following tags: {}", v("groupingIdentifiers", groupingTags));
        this.groupingTags = groupingTags;
    }

    public void setSSHPrivateKeys (@NotNull Map<String, String> sshPrivateKeys) {
        log.info("Loading private keys for keynames: {}", v("key-name", sshPrivateKeys.keySet()));
        this.sshPrivateKeys = sshPrivateKeys;
    }

    public Map<String, String> getImageIdToUsernameMap () {
        return imageIdToUsernameMap;
    }

    public void setImageIdToUsernameMap (Map<String, String> imageIdToUsernameMap) {
        this.imageIdToUsernameMap = new HashMap<>(imageIdToUsernameMap);
    }

    /**
     * Runs an API call and tests that it returns without issue. Any exceptions returns an API Error
     *
     * @return OK if call resolves, or ERROR if the call fails.
     */
    @Override
    public ApiStatus getApiStatus () {
        try {
            amazonEC2.describeInstances();
            return ApiStatus.OK;
        } catch (RuntimeException e) {
            log.error("API for AWS EC2 failed to resolve.", e);
            return ApiStatus.ERROR;
        }
    }

    @JsonIgnore
    public Collection<SubnetUtils.SubnetInfo> getRoutableCidrBlocks () {
        return routableCidrBlocks;
    }

    public void setRoutableCidrBlocks (Collection<String> routableCidrBlocks) {
        try {
            this.routableCidrBlocks = routableCidrBlocks.stream().map(SubnetUtils::new).map(SubnetUtils::getInfo).collect(Collectors.toSet());
        } catch (RuntimeException e) {
            ChaosException exception = new ChaosException(INVALID_CIDR_BLOCK, e);
            log.error("Invalid CIDR Blocks provided", exception);
            throw exception;
        }
    }

    public boolean isAddressRoutable (String privateIPAddress) {
        return getRoutableCidrBlocks().stream().anyMatch(subnetInfo -> {
            try {
                return subnetInfo.isInRange(privateIPAddress);
            } catch (RuntimeException e) {
                return false;
            }
        });
    }

    @Override
    public PlatformLevel getPlatformLevel () {
        return PlatformLevel.IAAS;
    }

    @Override
    public PlatformHealth getPlatformHealth () {
        Stream<Instance> instances = getInstanceStream();
        Set<InstanceState> instanceStates = instances.map(Instance::getState).collect(Collectors.toSet());
        Set<Integer> instanceStateCodes = instanceStates.stream().map(InstanceState::getCode).collect(Collectors.toSet());
        for (int state : AwsEC2Constants.getAwsUnhealthyCodes()) {
            if (instanceStateCodes.contains(state)) return PlatformHealth.DEGRADED;
        }
        return PlatformHealth.OK;
    }

    @Override
    public List<Container> generateRoster () {
        final List<Container> containerList = new ArrayList<>();
        boolean done = false;
        DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest().withFilters(generateSearchFilters());
        while (!done) {
            DescribeInstancesResult describeInstancesResult = amazonEC2.describeInstances(describeInstancesRequest);
            containerList.addAll(describeInstancesResult.getReservations()
                                                        .stream()
                                                        .map(Reservation::getInstances)
                                                        .flatMap(Collection::parallelStream)
                                                        .filter(this::hasAvailabilityZone)
                                                        .filter(not(instance -> awsEC2SelfAwareness.isMe(instance.getInstanceId())))
                                                        .map(this::createContainerFromInstance)
                                                        .filter(Objects::nonNull)
                                                        .collect(Collectors.toSet()));
            describeInstancesRequest.setNextToken(describeInstancesResult.getNextToken());
            if (describeInstancesRequest.getNextToken() == null) {
                done = true;
                // Loops until all pages of instances have been resolved
            }
        }
        if (containerList.isEmpty()) {
            log.warn("No matching EC2 instance found.");
        }
        return containerList;
    }

    private boolean hasAvailabilityZone (Instance instance) {
        return !Optional.of(instance)
                        .map(Instance::getPlacement)
                        .map(Placement::getAvailabilityZone)
                        .map(String::isBlank)
                        .orElse(true);
    }

    @Override
    protected String getDefaultUngroupedAggregationIdentifier () {
        return AwsEC2Constants.NO_GROUPING_IDENTIFIER;
    }

    @Override
    public boolean isContainerRecycled (Container container) {
        AwsEC2Container awsEC2Container;
        if (!(container instanceof AwsEC2Container)) return false;
        awsEC2Container = (AwsEC2Container) container;
        String instanceId = awsEC2Container.getInstanceId();
        return getInstanceStream().filter(not(instance -> instance.getState().getCode().equals(AWS_TERMINATED_CODE))).noneMatch(instance -> instanceId.equals(instance.getInstanceId()));
    }

    /**
     * Creates a Container object from an EC2 Instance and appends it to a provided list of containers.
     *
     * @param instance An EC2 Instance object to have a container created.
     */
    AwsEC2Container createContainerFromInstance (Instance instance) {
        if (instance.getState().getCode() == AwsEC2Constants.AWS_TERMINATED_CODE) return null;
        AwsEC2Container container = containerManager.getMatchingContainer(AwsEC2Container.class, instance.getInstanceId());
        if (container == null) {
            container = buildContainerFromInstance(instance);
            log.info("Found new AWS EC2 Container {}", v(DATADOG_CONTAINER_KEY, container));
            containerManager.offer(container);
        } else {
            log.debug("Found existing AWS EC2 Container {}", v(DATADOG_CONTAINER_KEY, container));
        }
        return container;
    }

    /**
     * Creates a Container object given an EC2 Instance object.
     *
     * @param instance Instance to have the Container created from.
     * @return Container mapping to the instance.
     */
    AwsEC2Container buildContainerFromInstance (Instance instance) {
        String groupIdentifier = null;
        Boolean nativeAwsAutoscaling = null;
        String name = instance.getTags()
                              .stream()
                              .filter(tag -> tag.getKey().equals("Name"))
                              .findFirst()
                              .orElse(new Tag("Name", "no-name"))
                              .getValue();
        if (groupingTags != null) {
            Tag groupingTag = instance.getTags()
                                      .stream()
                                      .filter(tag -> groupingTags.contains(tag.getKey()))
                                      .min(Comparator.comparingInt(tag -> groupingTags.indexOf(tag.getKey())))
                                      .orElse(null);
            groupIdentifier = groupingTag == null ? null : groupingTag.getValue();
            nativeAwsAutoscaling = groupingTag != null && AWS_ASG_NAME_TAG_KEY.equals(groupingTag.getKey());
        }
        Placement placement = instance.getPlacement();
        String availabilityZone = placement != null ? placement.getAvailabilityZone() : AwsConstants.NO_AZ_INFORMATION;
        return AwsEC2Container.builder().awsEC2Platform(this)
                              .instanceId(instance.getInstanceId())
                              .keyName(Optional.ofNullable(instance.getKeyName()).orElse(NO_ASSIGNED_KEY))
                              .name(name)
                              .groupIdentifier(Optional.ofNullable(groupIdentifier).orElse(NO_GROUPING_IDENTIFIER))
                              .nativeAwsAutoscaling(Optional.ofNullable(nativeAwsAutoscaling).orElse(false))
                              .availabilityZone(Optional.ofNullable(availabilityZone).orElse(AwsConstants.NO_AZ_INFORMATION))
                              .publicAddress(instance.getPublicIpAddress()).imageId(instance.getImageId())
                              .privateAddress(instance.getPrivateIpAddress())
                              .build();
    }

    private Stream<Instance> getInstanceStream () {
        return getInstanceStream(new DescribeInstancesRequest().withFilters(generateSearchFilters()));
    }

    private Stream<Instance> getInstanceStream (DescribeInstancesRequest describeInstancesRequest) {
        return amazonEC2.describeInstances(describeInstancesRequest)
                        .getReservations()
                        .stream()
                        .map(Reservation::getInstances)
                        .flatMap(List::stream);
    }

    Collection<Filter> generateSearchFilters () {
        return filter.entrySet().stream().map(this::createFilterFromEntry).collect(Collectors.toSet());
    }

    private Filter createFilterFromEntry (Map.Entry<String, List<String>> entry) {
        Filter newFilter = new Filter().withValues(entry.getValue());
        String name = entry.getKey();
        if (name.startsWith("tag.")) {
            newFilter.setName("tag:" + name.substring(4));
        } else {
            newFilter.setName(name.replaceAll("(?<!^)([A-Z])", "-$1").toLowerCase());
        }
        return newFilter;
    }

    public ContainerHealth checkHealth (String instanceId) {
        Instance instance;
        InstanceState state;
        DescribeInstancesRequest request = new DescribeInstancesRequest().withInstanceIds(instanceId);
        DescribeInstancesResult result = amazonEC2.describeInstances(request);
        try {
            instance = result.getReservations().get(0).getInstances().get(0);
            state = instance.getState();
            if (state.getCode() == 48) {
                log.info("Instance {} is terminated", v(DataDogConstants.EC2_INSTANCE, instance));
                return ContainerHealth.DOES_NOT_EXIST;
            }
        } catch (IndexOutOfBoundsException | NullPointerException e) {
            // If Index 0 in array doesn't exist, or we get an NPE, it's because the instance doesn't exist anymore.
            log.error("Instance {} doesn't seem to exist anymore", instanceId, e);
            return ContainerHealth.DOES_NOT_EXIST;
        }
        return state.getCode() == AwsEC2Constants.AWS_RUNNING_CODE ? ContainerHealth.NORMAL : ContainerHealth.RUNNING_EXPERIMENT;
    }

    public void stopInstance (String... instanceIds) {
        log.info("Requesting a stop of instances {}", instanceIds);
        amazonEC2.stopInstances(new StopInstancesRequest().withForce(true).withInstanceIds(instanceIds));
    }

    public void terminateInstance (String... instanceIds) {
        log.info("Requesting a Terminate of instances {}", instanceIds);
        amazonEC2.terminateInstances(new TerminateInstancesRequest().withInstanceIds(instanceIds));
    }

    public void startInstance (String... instanceIds) {
        log.info("Requesting a start of instances {}", instanceIds);
        amazonEC2.startInstances(new StartInstancesRequest().withInstanceIds(instanceIds));
    }

    public void restartInstance (String... instanceIds) {
        log.info("Requesting a reboot of instances {}", instanceIds);
        amazonEC2.rebootInstances(new RebootInstancesRequest().withInstanceIds(instanceIds));
    }

    public void setSecurityGroupIds (String networkInterfaceId, Collection<String> securityGroupIds) {
        try {
            log.debug("Setting security groups for interface {} to {}", networkInterfaceId, securityGroupIds);
            amazonEC2.modifyNetworkInterfaceAttribute(new ModifyNetworkInterfaceAttributeRequest().withNetworkInterfaceId(networkInterfaceId)
                                                                                                  .withGroups(securityGroupIds));
        } catch (AmazonEC2Exception e) {
            if (SECURITY_GROUP_NOT_FOUND.equals(e.getErrorCode())) {
                log.warn("Tried to set invalid security groups. Pruning out Chaos Security Group Map");
                processInvalidGroups(securityGroupIds);
            }
            throw new ChaosException(AWS_EC2_GENERIC_API_ERROR, e);
        }
    }

    void processInvalidGroups (Collection<String> securityGroupIds) {
        log.info("Removing {} from cached Security Groups for Chaos", v("Security Group IDs", securityGroupIds));
        vpcToSecurityGroupMap.values().removeAll(securityGroupIds);
    }

    public String getChaosSecurityGroupForInstance (String instanceId) {
        String vpcId = getVpcIdOfContainer(instanceId);
        return vpcToSecurityGroupMap.computeIfAbsent(vpcId, this::lookupChaosSecurityGroup);
    }

    String getVpcIdOfContainer (String instanceId) {
        return amazonEC2.describeInstances(new DescribeInstancesRequest().withInstanceIds(instanceId))
                        .getReservations()
                        .stream()
                        .map(Reservation::getInstances)
                        .flatMap(Collection::stream)
                        .peek(instance -> log.info("Lookup of VPCs for Instance {} shows {}", instanceId, instance.getVpcId()))
                        .findFirst().orElseThrow(NO_INSTANCES_RETURNED.asChaosException())
                        .getVpcId();
    }

    String lookupChaosSecurityGroup (String vpcId) {
        log.debug("Looking up Security Group to use for experiments for VPC {}", v("VPC", vpcId));
        return amazonEC2.describeSecurityGroups(new DescribeSecurityGroupsRequest().withFilters(new Filter("vpc-id").withValues(vpcId)))
                        .getSecurityGroups()
                        .stream()
                        .filter(securityGroup -> securityGroup.getVpcId().equals(vpcId))
                        .filter(securityGroup -> securityGroup.getGroupName().equals(EC2_DEFAULT_CHAOS_SECURITY_GROUP_NAME + "-" + vpcId))
                        .map(SecurityGroup::getGroupId)
                        .findFirst()
                        .orElseGet(() -> createChaosSecurityGroup(vpcId));
    }

    String createChaosSecurityGroup (String vpcId) {
        log.debug("Creating Chaos Security Group for VPC {}", v("VPC", vpcId));
        String groupId = amazonEC2.createSecurityGroup(new CreateSecurityGroupRequest().withGroupName(EC2_DEFAULT_CHAOS_SECURITY_GROUP_NAME + "-" + vpcId)
                                                                                       .withVpcId(vpcId)
                                                                                       .withDescription(EC2_DEFAULT_CHAOS_SECURITY_GROUP_DESCRIPTION))
                                  .getGroupId();
        amazonEC2.revokeSecurityGroupEgress(new RevokeSecurityGroupEgressRequest().withIpPermissions(DEFAULT_IP_PERMISSIONS)
                                                                                  .withGroupId(groupId));
        log.info("Created Security Group {} in VPC {} for use in Chaos", v("Security Group", groupId), v("VPC", vpcId));
        return groupId;
    }

    public boolean isContainerTerminated (String instanceId) {
        return amazonEC2.describeInstances(new DescribeInstancesRequest().withInstanceIds(instanceId))
                        .getReservations()
                        .stream()
                        .map(Reservation::getInstances)
                        .flatMap(Collection::stream)
                        .anyMatch(instance -> instance.getState().getCode() == AwsEC2Constants.AWS_TERMINATED_CODE);
    }

    public boolean isAutoscalingGroupAtDesiredInstances (String autoScalingGroupName) {
        List<AutoScalingGroup> autoScalingGroups = amazonAutoScaling.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest()
                .withAutoScalingGroupNames(autoScalingGroupName)).getAutoScalingGroups();
        int desiredCapacity = autoScalingGroups.stream().findFirst().map(AutoScalingGroup::getDesiredCapacity).orElse(1);
        int actualCapacity = (int) autoScalingGroups.stream()
                                                    .map(AutoScalingGroup::getInstances)
                                                    .flatMap(Collection::stream)
                                                    .filter(instance -> instance.getHealthStatus().equals("Healthy"))
                                                    .count();
        return desiredCapacity == actualCapacity;
    }

    public boolean hasKey (String keyName) {
        return sshPrivateKeys.containsKey(keyName);
    }

    @Override
    public void recycleContainer (AwsEC2Container container) {
        triggerAutoscalingUnhealthy(container.getInstanceId());
    }

    public void triggerAutoscalingUnhealthy (String instanceId) {
        log.info("Manually setting instance {} as Unhealthy so Autoscaling corrects it.", v(DataDogConstants.RDS_INSTANCE_ID, instanceId));
        amazonAutoScaling.setInstanceHealth(new SetInstanceHealthRequest().withHealthStatus("Unhealthy")
                                                                          .withInstanceId(instanceId)
                                                                          .withShouldRespectGracePeriod(false));
    }

    @Override
    public String getEndpoint (AwsEC2Container container) {
        return container.getRoutableAddress();
    }

    @Override
    public SSHCredentials getSshCredentials (AwsEC2Container container) {
        return new ChaosSSHCredentials().withUsername(getUsernameForContainer(container)).withKeyPair(sshPrivateKeys.get(container.getKeyName()), null);
    }

    String getUsernameForContainer (AwsEC2Container container) {
        return getUsernameForImageId(container.getImageId());
    }

    String getUsernameForImageId (String imageId) {
        final Optional<String> usernameLookup = Optional.ofNullable(getImageIdToUsernameMap().get(imageId));
        usernameLookup.ifPresentOrElse(username -> log.info("Using username {} for SSH", username), () -> log.debug("Using username ec2-user for SSH"));
        return usernameLookup.orElse(DEFAULT_EC2_CLI_USER);
    }

    public boolean isStarted (AwsEC2Container awsEC2Container) {
        return isStarted(awsEC2Container.getInstanceId());
    }

    private boolean isStarted (String instanceId) {
        return amazonEC2.describeInstances(new DescribeInstancesRequest().withInstanceIds(instanceId))
                        .getReservations()
                        .stream()
                        .map(Reservation::getInstances)
                        .flatMap(List::stream)
                        .filter(instance -> instance.getInstanceId().equals(instanceId))
                        .findFirst()
                        .map(Instance::getState)
                        .map(InstanceState::getCode)
                        .map(i -> AWS_RUNNING_CODE == i)
                        .orElse(false);
    }

    public Map<String, Set<String>> getNetworkInterfaceToSecurityGroupsMap (String instanceId) {
        log.debug("Looking up security groups for instance {}", instanceId);
        return amazonEC2.describeNetworkInterfaces(new DescribeNetworkInterfacesRequest().withFilters(new Filter("attachment.instance-id", List
                .of(instanceId))))
                        .getNetworkInterfaces()
                        .stream()
                        .collect(Collectors.groupingBy(NetworkInterface::getNetworkInterfaceId, Collectors.flatMapping((Function<? super NetworkInterface, ? extends Stream<String>>) networkInterface -> networkInterface
                                .getGroups()
                                .stream()
                                .map(GroupIdentifier::getGroupId), Collectors.toSet())));
    }

    public boolean verifySecurityGroupIdsOfNetworkInterfaceMap (String instanceId, Map<String, Set<String>> originalSecurityGroups) {
        return getNetworkInterfaceToSecurityGroupsMap(instanceId).equals(originalSecurityGroups);
    }
}
