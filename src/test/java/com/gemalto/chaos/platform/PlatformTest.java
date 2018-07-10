package com.gemalto.chaos.platform;

import com.gemalto.chaos.attack.enums.AttackType;
import com.gemalto.chaos.container.Container;
import com.gemalto.chaos.platform.enums.ApiStatus;
import com.gemalto.chaos.platform.enums.PlatformHealth;
import com.gemalto.chaos.platform.enums.PlatformLevel;
import org.hamcrest.collection.IsIterableContainingInAnyOrder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;

@RunWith(MockitoJUnitRunner.class)
public class PlatformTest {
    @Mock
    private Container container;
    private Platform platform;

    @Before
    public void setUp () {
        platform = new Platform() {
            @Override
            public List<Container> getRoster () {
                return Collections.singletonList(container);
            }

            @Override
            public ApiStatus getApiStatus () {
                return null;
            }

            @Override
            public PlatformLevel getPlatformLevel () {
                return null;
            }

            @Override
            public PlatformHealth getPlatformHealth () {
                return null;
            }
        };
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void getSupportedAttackTypes () {
        doReturn(Collections.singletonList(AttackType.STATE)).when(container).getSupportedAttackTypes();
        assertThat(platform.getSupportedAttackTypes(), IsIterableContainingInAnyOrder.containsInAnyOrder(AttackType.STATE));
        assertThat(platform.getSupportedAttackTypes(), IsIterableContainingInAnyOrder.containsInAnyOrder(AttackType.STATE));
        Mockito.verify(container, times(1)).getSupportedAttackTypes();
    }
}