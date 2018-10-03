package com.gemalto.chaos.container.impl;

import com.gemalto.chaos.ChaosException;
import com.gemalto.chaos.attack.Attack;
import com.gemalto.chaos.platform.impl.AwsRDSPlatform;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.Repeat;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.*;

import static org.hamcrest.Matchers.isOneOf;
import static org.hamcrest.collection.IsIn.isIn;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

@RunWith(SpringJUnit4ClassRunner.class)
public class AwsRDSClusterContainerTest {
    private AwsRDSClusterContainer awsRDSClusterContainer;
    @MockBean
    private AwsRDSPlatform awsRDSPlatform;
    private String dbClusterIdentifier = UUID.randomUUID().toString();
    private String engine = UUID.randomUUID().toString();

    @Before
    public void setUp () {
        awsRDSClusterContainer = Mockito.spy(AwsRDSClusterContainer.builder()
                                                                   .withAwsRDSPlatform(awsRDSPlatform)
                                                                   .withDbClusterIdentifier(dbClusterIdentifier)
                                                                   .withEngine(engine)
                                                                   .build());
    }

    @Test
    public void getDbClusterIdentifier () {
        assertEquals(dbClusterIdentifier, awsRDSClusterContainer.getDbClusterIdentifier());
    }

    @Test
    public void getPlatform () {
        assertEquals(awsRDSPlatform, awsRDSClusterContainer.getPlatform());
    }

    @Test
    public void getSimpleName () {
        assertEquals(dbClusterIdentifier, awsRDSClusterContainer.getSimpleName());
    }

    @Test
    public void getMembers () {
        awsRDSClusterContainer.getMembers();
        verify(awsRDSPlatform, times(1)).getClusterInstances(dbClusterIdentifier);
    }

    @Test
    @Repeat(value = 10)
    @SuppressWarnings("unchecked")
    public void getSomeMembers () {
        Set<String> members = new HashSet<>();
        String instanceId1 = UUID.randomUUID().toString();
        String instanceId2 = UUID.randomUUID().toString();
        members.add(instanceId1);
        members.add(instanceId2);
        when(awsRDSClusterContainer.getMembers()).thenReturn(members);
        assertThat(awsRDSClusterContainer.getSomeMembers(), isOneOf(Collections.singleton(instanceId1), Collections.singleton(instanceId2)));
        String instanceId3 = UUID.randomUUID().toString();
        members.add(instanceId3);
        Set<Set<String>> validSets;
        Set<String> set12 = new HashSet<>(Arrays.asList(instanceId1, instanceId2));
        Set<String> set21 = new HashSet<>(Arrays.asList(instanceId2, instanceId1));
        Set<String> set13 = new HashSet<>(Arrays.asList(instanceId1, instanceId3));
        Set<String> set31 = new HashSet<>(Arrays.asList(instanceId1, instanceId1));
        Set<String> set23 = new HashSet<>(Arrays.asList(instanceId2, instanceId3));
        Set<String> set32 = new HashSet<>(Arrays.asList(instanceId3, instanceId2));
        validSets = new HashSet<>(Arrays.asList(set12, set21, set13, set31, set23, set32, Collections.singleton(instanceId1), Collections
                .singleton(instanceId2), Collections.singleton(instanceId3)));
        assertThat(awsRDSClusterContainer.getSomeMembers(), isIn(validSets));
    }

    @Test(expected = ChaosException.class)
    public void getSomeMembersException () {
        Set<String> members = Collections.singleton("Single Member");
        doReturn(members).when(awsRDSPlatform).getClusterInstances(dbClusterIdentifier);
        awsRDSClusterContainer.getSomeMembers();
    }

    @Test
    public void restartInstances () {
        String instanceId1 = UUID.randomUUID().toString();
        String instanceId2 = UUID.randomUUID().toString();
        Attack attack = mock(Attack.class);
        Set<String> members = new HashSet<>(Arrays.asList(instanceId1, instanceId2));
        doReturn(members).when(awsRDSClusterContainer).getSomeMembers();
        awsRDSClusterContainer.restartInstances(attack);
        verify(attack, times(1)).setCheckContainerHealth(any());
        verify(awsRDSPlatform, times(1)).restartInstance(members.toArray(new String[0]));
    }

    @Test
    public void initiateFailover () {
        Attack attack = mock(Attack.class);
        doReturn(null).when(awsRDSClusterContainer).getSomeMembers();
        awsRDSClusterContainer.initiateFailover(attack);
        verify(attack, times(1)).setCheckContainerHealth(any());
        verify(awsRDSPlatform, times(1)).failoverCluster(dbClusterIdentifier);
    }

    @Test
    public void getUniqueIdentifier () {
        assertEquals(dbClusterIdentifier, awsRDSClusterContainer.getUniqueIdentifier());
    }
}