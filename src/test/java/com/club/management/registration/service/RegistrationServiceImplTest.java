package com.club.management.registration.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.club.management.activity.entity.Activity;
import com.club.management.activity.mapper.ActivityMapper;
import com.club.management.club.mapper.ClubInfoMapper;
import com.club.management.common.config.MyBatisPlusConfig;
import com.club.management.common.response.PageResult;
import com.club.management.common.util.TenantContext;
import com.club.management.registration.dto.RegistrationQueryDTO;
import com.club.management.registration.mapper.RegistrationMapper;
import com.club.management.registration.service.impl.RegistrationServiceImpl;
import com.club.management.registration.vo.RegistrationVO;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class RegistrationServiceImplTest {

    private SqlSession sqlSession;
    private Connection fixtureConnection;
    private ActivityMapper activityMapper;
    private RegistrationServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:registrations_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        fixtureConnection = dataSource.getConnection();
        createFixtures();

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(RegistrationMapper.class);
        configuration.addMapper(ActivityMapper.class);
        configuration.addMapper(ClubInfoMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setPlugins(new MyBatisPlusConfig().mybatisPlusInterceptor());
        sqlSession = factory.getObject().openSession(true);

        activityMapper = sqlSession.getMapper(ActivityMapper.class);
        service = new RegistrationServiceImpl();
        ReflectionTestUtils.setField(service, "registrationMapper", sqlSession.getMapper(RegistrationMapper.class));
        ReflectionTestUtils.setField(service, "activityMapper", activityMapper);
        ReflectionTestUtils.setField(service, "clubInfoMapper", sqlSession.getMapper(ClubInfoMapper.class));
        TenantContext.setClubId(1L);
    }

    @AfterEach
    void tearDown() throws Exception {
        TenantContext.clear();
        if (sqlSession != null) {
            sqlSession.close();
        }
        if (fixtureConnection != null) {
            fixtureConnection.close();
        }
    }

    @Test
    void myRegistrationsIncludesActivityDetailsAcrossClubs() {
        PageResult<RegistrationVO> result = service.myRegistrations(3L, new RegistrationQueryDTO());

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getList()).extracting(RegistrationVO::getActivityTitle)
                .containsExactly("Cross-club workshop", "Home-club workshop");
        assertThat(result.getList()).extracting(RegistrationVO::getClubName,
                        RegistrationVO::getLocation, RegistrationVO::getStartTime,
                        RegistrationVO::getStatus, RegistrationVO::getRegisteredAt)
                .containsExactly(
                        tuple("Photography Club", "Studio B", "2026-11-02T14:00", 2, "2026-10-27T10:00"),
                        tuple("Programming Club", "Lab A", "2026-11-01T09:00", 1, "2026-10-26T10:00"));
    }

    @Test
    void myRegistrationsKeepsPersonalPaginationAndStatusFiltering() {
        RegistrationQueryDTO query = new RegistrationQueryDTO();
        query.setPageSize(1);

        PageResult<RegistrationVO> firstPage = service.myRegistrations(3L, query);
        assertThat(firstPage.getTotal()).isEqualTo(2);
        assertThat(firstPage.getPageNum()).isEqualTo(1);
        assertThat(firstPage.getPageSize()).isEqualTo(1);
        assertThat(firstPage.getList()).extracting(RegistrationVO::getRegistrationId).containsExactly(202L);

        query.setPageNum(2);
        PageResult<RegistrationVO> secondPage = service.myRegistrations(3L, query);
        assertThat(secondPage.getTotal()).isEqualTo(2);
        assertThat(secondPage.getList()).extracting(RegistrationVO::getRegistrationId).containsExactly(201L);

        query.setPageNum(1);
        query.setStatus(1);
        PageResult<RegistrationVO> filtered = service.myRegistrations(3L, query);
        assertThat(filtered.getTotal()).isEqualTo(1);
        assertThat(filtered.getList()).extracting(RegistrationVO::getRegistrationId).containsExactly(201L);
    }

    @Test
    void ordinaryActivityQueriesStillRespectClubIsolation() {
        assertThat(activityMapper.selectBatchIds(List.of(101L, 102L)))
                .extracting(Activity::getId).containsExactly(101L);
    }

    @Test
    void registeredActivityQueryExcludesOtherUsersAndDeletedRegistrations() {
        assertThat(activityMapper.selectRegisteredActivities(3L, List.of(101L, 102L, 103L, 104L, 105L)))
                .extracting(Activity::getId).containsExactlyInAnyOrder(101L, 102L);
        assertThat(activityMapper.selectRegisteredActivities(3L, List.of(102L)))
                .extracting(Activity::getId).containsExactly(102L);
        assertThat(activityMapper.selectRegisteredActivities(999L, List.of(101L, 102L, 103L, 104L, 105L)))
                .isEmpty();
    }

    @Test
    void registeredActivityQueryExcludesDeletedActivities() {
        assertThat(activityMapper.selectRegisteredActivities(4L, List.of(101L, 102L, 103L, 104L, 105L)))
                .extracting(Activity::getId).containsExactly(103L);
    }

    private void createFixtures() throws Exception {
        try (Statement statement = fixtureConnection.createStatement()) {
            statement.execute("""
                    CREATE TABLE reg_registration (
                        id BIGINT PRIMARY KEY, activity_id BIGINT, user_id BIGINT,
                        status INT, registered_at TIMESTAMP,
                        created_by BIGINT, created_at TIMESTAMP, updated_by BIGINT,
                        updated_at TIMESTAMP, deleted INT DEFAULT 0
                    )
                    """);
            statement.execute("""
                    CREATE TABLE act_activity (
                        id BIGINT PRIMARY KEY, club_id BIGINT, title VARCHAR(100),
                        description VARCHAR(500), type VARCHAR(20), location VARCHAR(200),
                        start_time TIMESTAMP, end_time TIMESTAMP, registration_deadline TIMESTAMP,
                        max_participants INT, current_participants INT, budget DECIMAL(10,2),
                        status VARCHAR(20), version INT, summary VARCHAR(500), cover_image VARCHAR(255),
                        need_registration BOOLEAN, need_approval BOOLEAN,
                        created_by BIGINT, created_at TIMESTAMP, updated_by BIGINT,
                        updated_at TIMESTAMP, deleted INT DEFAULT 0
                    )
                    """);
            statement.execute("""
                    CREATE TABLE club_info (
                        id BIGINT PRIMARY KEY, club_name VARCHAR(100), category VARCHAR(50),
                        description VARCHAR(500), charter VARCHAR(500), cover_image VARCHAR(255),
                        president_id BIGINT, approval_status INT, reject_reason VARCHAR(500),
                        approved_by BIGINT, approved_at TIMESTAMP, member_count INT,
                        created_by BIGINT, created_at TIMESTAMP, updated_by BIGINT,
                        updated_at TIMESTAMP, deleted INT DEFAULT 0
                    )
                    """);
            statement.execute("""
                    INSERT INTO club_info (id, club_name) VALUES
                    (1, 'Programming Club'), (2, 'Photography Club')
                    """);
            statement.execute("""
                    INSERT INTO act_activity (id, club_id, title, location, start_time, deleted) VALUES
                    (101, 1, 'Home-club workshop', 'Lab A', '2026-11-01 09:00:00', 0),
                    (102, 2, 'Cross-club workshop', 'Studio B', '2026-11-02 14:00:00', 0),
                    (103, 2, 'Other user workshop', 'Studio C', '2026-11-03 14:00:00', 0),
                    (104, 2, 'Deleted workshop', 'Studio D', '2026-11-04 14:00:00', 1),
                    (105, 2, 'Cancelled record workshop', 'Studio E', '2026-11-05 14:00:00', 0)
                    """);
            statement.execute("""
                    INSERT INTO reg_registration (id, activity_id, user_id, status, registered_at, deleted) VALUES
                    (201, 101, 3, 1, '2026-10-26 10:00:00', 0),
                    (202, 102, 3, 2, '2026-10-27 10:00:00', 0),
                    (203, 103, 4, 1, '2026-10-28 10:00:00', 0),
                    (204, 104, 4, 1, '2026-10-29 10:00:00', 0),
                    (205, 105, 3, 1, '2026-10-30 10:00:00', 1)
                    """);
        }
    }
}
