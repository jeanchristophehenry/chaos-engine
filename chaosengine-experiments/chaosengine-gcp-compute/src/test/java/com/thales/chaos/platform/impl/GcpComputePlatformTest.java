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

import com.google.cloud.compute.v1.*;
import com.thales.chaos.constants.GcpConstants;
import com.thales.chaos.container.impl.GcpComputeInstanceContainer;
import com.thales.chaos.platform.enums.PlatformLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.Map;

import static com.thales.chaos.services.impl.GcpComputeService.COMPUTE_PROJECT;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@ContextConfiguration
public class GcpComputePlatformTest {
    private static final String MY_AWESOME_PROJECT = "my-awesome-project";
    @MockBean
    private InstanceClient instanceClient;
    @MockBean
    private InstanceGroupClient instanceGroupClient;
    @MockBean
    private InstanceGroupManagerClient instanceGroupManagerClient;
    @MockBean
    private RegionInstanceGroupClient regionInstanceGroupClient;
    @MockBean
    private RegionInstanceGroupManagerClient regionInstanceGroupManagerClient;
    @Autowired
    private ProjectName projectName;
    @Autowired
    private GcpComputePlatform gcpComputePlatform;

    @Test
    public void isIAASPlatform () {
        assertEquals(PlatformLevel.IAAS, gcpComputePlatform.getPlatformLevel());
    }

    @Test
    public void createContainerFromInstanceWithNoDetails () {
        Instance instance = Instance.newBuilder().build();
        GcpComputeInstanceContainer container = GcpComputeInstanceContainer.builder().build();
        assertEquals(container, gcpComputePlatform.createContainerFromInstance(instance));
    }

    @Test
    public void createContainerFromInstanceWithDetails () {
        String createdByValue = "My-VM-Instance-Group";
        Metadata metadata = Metadata.newBuilder()
                                    .addItems(Items.newBuilder()
                                                   .setKey(GcpConstants.CREATED_BY_METADATA_KEY)
                                                   .setValue(createdByValue)
                                                   .build())
                                    .build();
        String tag1 = "HTTP";
        String tag2 = "SSH";
        String tag3 = "RDP";
        Tags tags = Tags.newBuilder().addAllItems(List.of(tag1, tag2, tag3)).build();
        String id = "123456789101112131415";
        String name = "My-Weird-SSH-and-RDP-Web-Server";
        String zone = "some-datacenter-somewhere";
        Instance instance = Instance.newBuilder()
                                    .setMetadata(metadata)
                                    .setName(name)
                                    .setId(id)
                                    .setTags(tags)
                                    .setZone(zone)
                                    .build();
        GcpComputeInstanceContainer container = GcpComputeInstanceContainer.builder()
                                                                           .withCreatedBy(createdByValue)
                                                                           .withFirewallTags(List.of(tag1, tag2, tag3))
                                                                           .withPlatform(gcpComputePlatform)
                                                                           .withInstanceName(name)
                                                                           .withUniqueIdentifier(id)
                                                                           .withZone(zone)
                                                                           .build();
        assertEquals(container, gcpComputePlatform.createContainerFromInstance(instance));
    }

    @Test
    public void generateRoster () {
        GcpComputeInstanceContainer expected = GcpComputeInstanceContainer.builder()
                                                                          .withUniqueIdentifier("12345678901234567890")
                                                                          .build();
        InstanceClient.AggregatedListInstancesPagedResponse response = mock(InstanceClient.AggregatedListInstancesPagedResponse.class);
        Instance instance = Instance.newBuilder().setId("12345678901234567890").build();
        Iterable<InstancesScopedList> iterableInstances = List.of(InstancesScopedList.newBuilder()
                                                                                     .addInstances(instance)
                                                                                     .build());
        doReturn(iterableInstances).when(response).iterateAll();
        doReturn(response).when(instanceClient).aggregatedListInstances(projectName);
        assertThat(gcpComputePlatform.generateRoster(), containsInAnyOrder(expected));
        verify(gcpComputePlatform).isNotFiltered(instance);
    }

    @Test
    public void startInstance () {
        String zone = "my-zone";
        String uniqueIdentifier = "12345678901234567890";
        GcpComputeInstanceContainer container = GcpComputeInstanceContainer.builder()
                                                                           .withUniqueIdentifier(uniqueIdentifier)
                                                                           .withZone(zone)
                                                                           .build();
        ProjectZoneInstanceName instanceName = GcpComputePlatform.getProjectZoneInstanceNameOfContainer(container,
                projectName);
        gcpComputePlatform.startInstance(container);
        verify(instanceClient).startInstance(instanceName);
    }

    @Test
    public void stopInstance () {
        String zone = "my-zone";
        String uniqueIdentifier = "12345678901234567890";
        GcpComputeInstanceContainer container = GcpComputeInstanceContainer.builder()
                                                                           .withUniqueIdentifier(uniqueIdentifier)
                                                                           .withZone(zone)
                                                                           .build();
        ProjectZoneInstanceName instanceName = GcpComputePlatform.getProjectZoneInstanceNameOfContainer(container,
                projectName);
        gcpComputePlatform.stopInstance(container);
        verify(instanceClient).stopInstance(instanceName);
    }

    @Test
    public void setTags () {
        String zone = "my-zone";
        String uniqueIdentifier = "12345678901234567890";
        GcpComputeInstanceContainer container = GcpComputeInstanceContainer.builder()
                                                                           .withUniqueIdentifier(uniqueIdentifier)
                                                                           .withZone(zone)
                                                                           .build();
        ProjectZoneInstanceName instanceName = GcpComputePlatform.getProjectZoneInstanceNameOfContainer(container,
                projectName);
        Tags tags = Tags.newBuilder().addItems("my-tag").addItems("my-other-tag").build();
        List<String> tagList = tags.getItemsList();
        gcpComputePlatform.setTags(container, tagList);
        verify(instanceClient).setTagsInstance(instanceName, tags);
    }

    @Test
    public void getProjectZoneInstanceName () {
        GcpComputeInstanceContainer container = GcpComputeInstanceContainer.builder()
                                                                           .withZone("my-zone")
                                                                           .withUniqueIdentifier("unique-identifier")
                                                                           .build();
        ProjectZoneInstanceName actualInstanceName = GcpComputePlatform.getProjectZoneInstanceNameOfContainer(container,
                projectName);
        ProjectZoneInstanceName expectedInstanceName = ProjectZoneInstanceName.newBuilder()
                                                                              .setZone("my-zone")
                                                                              .setInstance("unique-identifier")
                                                                              .setProject(projectName.getProject())
                                                                              .build();
        assertEquals(actualInstanceName, expectedInstanceName);
    }

    @Test
    public void isNotFiltered () {
        Items includeTags = Items.newBuilder().setKey("include").setValue("true").build();
        Metadata includeMetadata = Metadata.newBuilder().addItems(includeTags).build();
        Items excludeTags = Items.newBuilder().setKey("exclude").setValue("true").build();
        Metadata excludeMetadata = Metadata.newBuilder().addItems(excludeTags).build();
        Instance matchingInclude = Instance.newBuilder().setMetadata(includeMetadata).build();
        Instance includeWithExclude = Instance.newBuilder(matchingInclude).setMetadata(excludeMetadata).build();
        gcpComputePlatform.setIncludeFilter(Map.of("include", "true"));
        gcpComputePlatform.setExcludeFilter(Map.of("exclude", "true"));
        assertTrue(gcpComputePlatform.isNotFiltered(matchingInclude));
        assertFalse(gcpComputePlatform.isNotFiltered(includeWithExclude));
    }

    @Test
    public void isContainerGroupAtDesiredCapacity () {
        final String INSTANCE_GROUP = "12345";
        final String PROJECT = "54321";
        final String ZONE = "my-datacenter";
        GcpComputeInstanceContainer container = GcpComputeInstanceContainer.builder()
                                                                           .withCreatedBy(ProjectZoneInstanceGroupName.of(
                                                                                   INSTANCE_GROUP,
                                                                                   PROJECT,
                                                                                   ZONE)
                                                                                                                      .toString()
                                                                                                                      .substring(
                                                                                                                              ProjectRegionInstanceGroupName.SERVICE_ADDRESS
                                                                                                                                      .length() - "projects/"
                                                                                                                                      .length()))
                                                                           .build();
        InstanceGroup instanceGroup = InstanceGroup.newBuilder().setSize(10).build();
        InstanceGroupManager instanceGroupManager = InstanceGroupManager.newBuilder().setTargetSize(10).build();
        doReturn(instanceGroup).when(instanceGroupClient)
                               .getInstanceGroup(ProjectZoneInstanceGroupName.of(INSTANCE_GROUP, PROJECT, ZONE));
        doReturn(instanceGroupManager).when(instanceGroupManagerClient)
                                      .getInstanceGroupManager(ProjectZoneInstanceGroupManagerName.of(INSTANCE_GROUP,
                                              PROJECT,
                                              ZONE));
        assertTrue(gcpComputePlatform.isContainerGroupAtCapacity(container));
    }

    @Test
    public void isContainerGroupBelowDesiredCapacity () {
        final String INSTANCE_GROUP = "12345";
        final String PROJECT = "54321";
        final String ZONE = "my-datacenter";
        GcpComputeInstanceContainer container = GcpComputeInstanceContainer.builder()
                                                                           .withCreatedBy(ProjectZoneInstanceGroupName.of(
                                                                                   INSTANCE_GROUP,
                                                                                   PROJECT,
                                                                                   ZONE)
                                                                                                                      .toString()
                                                                                                                      .substring(
                                                                                                                              ProjectRegionInstanceGroupName.SERVICE_ADDRESS
                                                                                                                                      .length() - "projects/"
                                                                                                                                      .length()))
                                                                           .build();
        InstanceGroup instanceGroup = InstanceGroup.newBuilder().setSize(9).build();
        InstanceGroupManager instanceGroupManager = InstanceGroupManager.newBuilder().setTargetSize(10).build();
        doReturn(instanceGroup).when(instanceGroupClient)
                               .getInstanceGroup(ProjectZoneInstanceGroupName.of(INSTANCE_GROUP, PROJECT, ZONE));
        doReturn(instanceGroupManager).when(instanceGroupManagerClient)
                                      .getInstanceGroupManager(ProjectZoneInstanceGroupManagerName.of(INSTANCE_GROUP,
                                              PROJECT,
                                              ZONE));
        assertFalse(gcpComputePlatform.isContainerGroupAtCapacity(container));
    }

    @Test
    public void isContainerGroupAboveDesiredCapacity () {
        final String INSTANCE_GROUP = "12345";
        final String PROJECT = "54321";
        final String ZONE = "my-datacenter";
        GcpComputeInstanceContainer container = GcpComputeInstanceContainer.builder()
                                                                           .withCreatedBy(ProjectZoneInstanceGroupName.of(
                                                                                   INSTANCE_GROUP,
                                                                                   PROJECT,
                                                                                   ZONE)
                                                                                                                      .toString()
                                                                                                                      .substring(
                                                                                                                              ProjectRegionInstanceGroupName.SERVICE_ADDRESS
                                                                                                                                      .length() - "projects/"
                                                                                                                                      .length()))
                                                                           .build();
        InstanceGroup instanceGroup = InstanceGroup.newBuilder().setSize(11).build();
        InstanceGroupManager instanceGroupManager = InstanceGroupManager.newBuilder().setTargetSize(10).build();
        doReturn(instanceGroup).when(instanceGroupClient)
                               .getInstanceGroup(ProjectZoneInstanceGroupName.of(INSTANCE_GROUP, PROJECT, ZONE));
        doReturn(instanceGroupManager).when(instanceGroupManagerClient)
                                      .getInstanceGroupManager(ProjectZoneInstanceGroupManagerName.of(INSTANCE_GROUP,
                                              PROJECT,
                                              ZONE));
        assertTrue(gcpComputePlatform.isContainerGroupAtCapacity(container));
    }

    @Test
    public void isContainerRegionGroupAtDesiredCapacity () {
        final String INSTANCE_GROUP = "12345";
        final String PROJECT = "54321";
        final String ZONE = "my-datacenter";
        GcpComputeInstanceContainer container = GcpComputeInstanceContainer.builder()
                                                                           .withCreatedBy(ProjectRegionInstanceGroupName
                                                                                   .of(INSTANCE_GROUP, PROJECT, ZONE)
                                                                                   .toString()
                                                                                   .substring(
                                                                                           ProjectRegionInstanceGroupName.SERVICE_ADDRESS
                                                                                                   .length() - "projects/"
                                                                                                   .length()))
                                                                           .build();
        InstanceGroup instanceGroup = InstanceGroup.newBuilder().setSize(10).build();
        InstanceGroupManager instanceGroupManager = InstanceGroupManager.newBuilder().setTargetSize(10).build();
        doReturn(instanceGroup).when(regionInstanceGroupClient)
                               .getRegionInstanceGroup(ProjectRegionInstanceGroupName.of(INSTANCE_GROUP,
                                       PROJECT,
                                       ZONE));
        doReturn(instanceGroupManager).when(regionInstanceGroupManagerClient)
                                      .getRegionInstanceGroupManager(ProjectRegionInstanceGroupManagerName.of(
                                              INSTANCE_GROUP,
                                              PROJECT,
                                              ZONE));
        assertTrue(gcpComputePlatform.isContainerGroupAtCapacity(container));
    }

    @Test
    public void isContainerRegionGroupBelowDesiredCapacity () {
        final String INSTANCE_GROUP = "12345";
        final String PROJECT = "54321";
        final String ZONE = "my-datacenter";
        GcpComputeInstanceContainer container = GcpComputeInstanceContainer.builder()
                                                                           .withCreatedBy(ProjectRegionInstanceGroupName
                                                                                   .of(INSTANCE_GROUP, PROJECT, ZONE)
                                                                                   .toString()
                                                                                   .substring(
                                                                                           ProjectRegionInstanceGroupName.SERVICE_ADDRESS
                                                                                                   .length() - "projects/"
                                                                                                   .length()))
                                                                           .build();
        InstanceGroup instanceGroup = InstanceGroup.newBuilder().setSize(10).build();
        InstanceGroupManager instanceGroupManager = InstanceGroupManager.newBuilder().setTargetSize(11).build();
        doReturn(instanceGroup).when(regionInstanceGroupClient)
                               .getRegionInstanceGroup(ProjectRegionInstanceGroupName.of(INSTANCE_GROUP,
                                       PROJECT,
                                       ZONE));
        doReturn(instanceGroupManager).when(regionInstanceGroupManagerClient)
                                      .getRegionInstanceGroupManager(ProjectRegionInstanceGroupManagerName.of(
                                              INSTANCE_GROUP,
                                              PROJECT,
                                              ZONE));
        assertFalse(gcpComputePlatform.isContainerGroupAtCapacity(container));
    }

    @Test
    public void isContainerRegionGroupAboveDesiredCapacity () {
        final String INSTANCE_GROUP = "12345";
        final String PROJECT = "54321";
        final String ZONE = "my-datacenter";
        GcpComputeInstanceContainer container = GcpComputeInstanceContainer.builder()
                                                                           .withCreatedBy(ProjectRegionInstanceGroupName
                                                                                   .of(INSTANCE_GROUP, PROJECT, ZONE)
                                                                                   .toString()
                                                                                   .substring(
                                                                                           ProjectRegionInstanceGroupName.SERVICE_ADDRESS
                                                                                                   .length() - "projects/"
                                                                                                   .length()))
                                                                           .build();
        InstanceGroup instanceGroup = InstanceGroup.newBuilder().setSize(11).build();
        InstanceGroupManager instanceGroupManager = InstanceGroupManager.newBuilder().setTargetSize(10).build();
        doReturn(instanceGroup).when(regionInstanceGroupClient)
                               .getRegionInstanceGroup(ProjectRegionInstanceGroupName.of(INSTANCE_GROUP,
                                       PROJECT,
                                       ZONE));
        doReturn(instanceGroupManager).when(regionInstanceGroupManagerClient)
                                      .getRegionInstanceGroupManager(ProjectRegionInstanceGroupManagerName.of(
                                              INSTANCE_GROUP,
                                              PROJECT,
                                              ZONE));
        assertTrue(gcpComputePlatform.isContainerGroupAtCapacity(container));
    }

    @Test
    public void isContainerGroupAtDesiredCapacityWithFullProjectLink () {
        final String INSTANCE_GROUP = "12345";
        final String PROJECT = "54321";
        final String ZONE = "my-datacenter";
        GcpComputeInstanceContainer container = GcpComputeInstanceContainer.builder()
                                                                           .withCreatedBy(ProjectZoneInstanceGroupName.of(
                                                                                   INSTANCE_GROUP,
                                                                                   PROJECT,
                                                                                   ZONE).toString())
                                                                           .build();
        InstanceGroup instanceGroup = InstanceGroup.newBuilder().setSize(10).build();
        InstanceGroupManager instanceGroupManager = InstanceGroupManager.newBuilder().setTargetSize(10).build();
        doReturn(instanceGroup).when(instanceGroupClient)
                               .getInstanceGroup(ProjectZoneInstanceGroupName.of(INSTANCE_GROUP, PROJECT, ZONE));
        doReturn(instanceGroupManager).when(instanceGroupManagerClient)
                                      .getInstanceGroupManager(ProjectZoneInstanceGroupManagerName.of(INSTANCE_GROUP,
                                              PROJECT,
                                              ZONE));
        assertTrue(gcpComputePlatform.isContainerGroupAtCapacity(container));
    }

    @Test
    public void isContainerRegionGroupAtDesiredCapacityWithFullProjectLink () {
        final String INSTANCE_GROUP = "12345";
        final String PROJECT = "54321";
        final String ZONE = "my-datacenter";
        GcpComputeInstanceContainer container = GcpComputeInstanceContainer.builder()
                                                                           .withCreatedBy(ProjectRegionInstanceGroupName
                                                                                   .of(INSTANCE_GROUP, PROJECT, ZONE)
                                                                                   .toString())
                                                                           .build();
        InstanceGroup instanceGroup = InstanceGroup.newBuilder().setSize(10).build();
        InstanceGroupManager instanceGroupManager = InstanceGroupManager.newBuilder().setTargetSize(10).build();
        doReturn(instanceGroup).when(regionInstanceGroupClient)
                               .getRegionInstanceGroup(ProjectRegionInstanceGroupName.of(INSTANCE_GROUP,
                                       PROJECT,
                                       ZONE));
        doReturn(instanceGroupManager).when(regionInstanceGroupManagerClient)
                                      .getRegionInstanceGroupManager(ProjectRegionInstanceGroupManagerName.of(
                                              INSTANCE_GROUP,
                                              PROJECT,
                                              ZONE));
        assertTrue(gcpComputePlatform.isContainerGroupAtCapacity(container));
    }

    @Configuration
    public static class GcpComputePlatformTestConfiguration {
        @Autowired
        private InstanceClient instanceClient;
        @Autowired
        private InstanceGroupClient instanceGroupClient;
        @Autowired
        private InstanceGroupManagerClient instanceGroupManagerClient;
        @Autowired
        private RegionInstanceGroupClient regionInstanceGroupClient;
        @Autowired
        private RegionInstanceGroupManagerClient regionInstanceGroupManagerClient;

        @Bean(name = COMPUTE_PROJECT)
        public ProjectName projectName () {
            return ProjectName.of(MY_AWESOME_PROJECT);
        }

        @Bean
        public GcpComputePlatform gcpComputePlatform () {
            return spy(new GcpComputePlatform());
        }
    }
}