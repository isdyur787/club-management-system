package com.club.management.activity.manager;

import com.club.management.common.enums.ActivityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalStrategyFactory 单元测试")
class ApprovalStrategyFactoryTest {

    @Mock
    private CampusApprovalStrategy campusStrategy;

    @Mock
    private OffCampusApprovalStrategy offCampusStrategy;

    @Mock
    private LargeApprovalStrategy largeStrategy;

    @InjectMocks
    private ApprovalStrategyFactory factory;

    @Test
    @DisplayName("CAMPUS 类型返回 CampusApprovalStrategy")
    void getStrategy_campus_returnsCampusStrategy() {
        ApprovalStrategy strategy = factory.getStrategy(ActivityType.CAMPUS);
        assertThat(strategy).isSameAs(campusStrategy);
    }

    @Test
    @DisplayName("OFF_CAMPUS 类型返回 OffCampusApprovalStrategy")
    void getStrategy_offCampus_returnsOffCampusStrategy() {
        ApprovalStrategy strategy = factory.getStrategy(ActivityType.OFF_CAMPUS);
        assertThat(strategy).isSameAs(offCampusStrategy);
    }

    @Test
    @DisplayName("LARGE 类型返回 LargeApprovalStrategy")
    void getStrategy_large_returnsLargeStrategy() {
        ApprovalStrategy strategy = factory.getStrategy(ActivityType.LARGE);
        assertThat(strategy).isSameAs(largeStrategy);
    }

    @Test
    @DisplayName("每种活动类型返回的策略不为 null")
    void getStrategy_allTypes_returnNonNull() {
        for (ActivityType type : ActivityType.values()) {
            assertThat(factory.getStrategy(type)).isNotNull();
        }
    }

    @Test
    @DisplayName("不同类型返回不同策略实例")
    void getStrategy_differentTypes_returnDifferentInstances() {
        ApprovalStrategy campus = factory.getStrategy(ActivityType.CAMPUS);
        ApprovalStrategy offCampus = factory.getStrategy(ActivityType.OFF_CAMPUS);
        ApprovalStrategy large = factory.getStrategy(ActivityType.LARGE);

        assertThat(campus).isNotSameAs(offCampus);
        assertThat(campus).isNotSameAs(large);
        assertThat(offCampus).isNotSameAs(large);
    }
}
