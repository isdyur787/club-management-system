# 模块详细设计文档

## 1. 用户模块

### 1.1 类设计

```
UserController
    ├── POST /auth/register    → register(@Valid RegisterDTO)
    ├── POST /auth/login       → login(@Valid LoginDTO)
    ├── GET  /auth/me          → getCurrentUser()
    ├── PUT  /users/me         → updateProfile(@Valid UserUpdateDTO)
    └── PUT  /users/me/password → changePassword(@Valid PasswordUpdateDTO)

UserService / UserServiceImpl
    ├── register(RegisterDTO): UserVO
    ├── login(LoginDTO): LoginVO
    ├── getCurrentUser(): UserVO
    ├── updateProfile(Long userId, UserUpdateDTO): void
    └── changePassword(Long userId, PasswordUpdateDTO): void

AuthManager
    ├── encodePassword(String rawPassword): String
    ├── validatePassword(String raw, String encoded): boolean
    └── generateToken(User user): String

UserMapper extends BaseMapper<User>
RoleMapper extends BaseMapper<Role>
```

### 1.2 注册流程

```
1. Controller 接收 RegisterDTO，@Valid 校验
2. Service.register():
   a. 检查 username 是否已存在 → 抛出 40102
   a. 检查 email 是否已存在 → 抛出 40102
   b. AuthManager.encodePassword() 加密密码
   c. 创建 User Entity，设置默认角色 STUDENT
   d. 保存 User + UserRole
   e. 发布 MemberJoinedEvent（可选）
   f. 返回 UserVO
```

### 1.3 登录流程

```
1. Controller 接收 LoginDTO
2. Service.login():
   a. 根据 username 查询 User → 不存在抛 40101
   b. AuthManager.validatePassword() 校验密码 → 错误抛 40103
   c. 检查用户状态 → 禁用则抛 40002
   d. AuthManager.generateToken() 生成 JWT
   e. 查询用户角色列表
   f. 返回 LoginVO（token + 用户信息 + 角色列表）
```

### 1.4 JWT Token 结构

```java
// JwtUtil.java
public class JwtUtil {
    private static final String SECRET = "${jwt.secret}";  // 从配置读取
    private static final long EXPIRATION = 86400000;       // 24小时

    public static String generateToken(Long userId, String username) { ... }
    public static Claims parseToken(String token) { ... }
    public static Long getUserId(String token) { ... }
    public static boolean isExpired(String token) { ... }
}
```

---

## 2. 社团模块

### 2.1 类设计

```
ClubController
    ├── POST   /clubs                    → createClub(@Valid ClubCreateDTO)
    ├── GET    /clubs                    → listClubs(ClubQueryDTO)
    ├── GET    /clubs/{id}               → getClubDetail(Long id)
    ├── PUT    /clubs/{id}               → updateClub(Long id, @Valid ClubUpdateDTO)
    ├── PUT    /clubs/{id}/approve       → approveClub(Long id, @Valid ApprovalDTO)
    ├── POST   /clubs/{id}/members       → joinClub(Long id, @Valid JoinDTO)
    ├── DELETE /clubs/{clubId}/members/me → leaveClub(Long clubId)
    ├── GET    /clubs/{id}/members       → listMembers(Long id, MemberQueryDTO)
    └── DELETE /clubs/{clubId}/members/{userId} → removeMember(Long clubId, Long userId)

ClubService / ClubServiceImpl
    ├── createClub(Long userId, ClubCreateDTO): ClubVO
    ├── listClubs(ClubQueryDTO): PageResult<ClubVO>
    ├── getClubDetail(Long id): ClubDetailVO
    ├── updateClub(Long id, ClubUpdateDTO): void
    ├── approveClub(Long id, ApprovalDTO): void
    ├── joinClub(Long clubId, Long userId): void
    ├── leaveClub(Long clubId, Long userId): void
    ├── listMembers(Long clubId, MemberQueryDTO): PageResult<MemberVO>
    └── removeMember(Long clubId, Long userId): void

ClubInfoMapper extends BaseMapper<ClubInfo>
ClubMemberMapper extends BaseMapper<ClubMember>
```

### 2.2 创建社团流程

```
1. Service.createClub(userId, dto):
   a. 检查 clubName 唯一性 → 40202
   b. 创建 ClubInfo Entity，设置 presidentId = userId，approvalStatus = 0
   c. 保存 ClubInfo
   d. 创建 ClubMember，role = PRESIDENT
   e. 保存 ClubMember
   f. 更新 club_info.member_count = 1
   g. 返回 ClubVO
```

### 2.3 加入社团流程

```
1. Service.joinClub(clubId, userId):
   a. 查询 ClubInfo → 不存在抛 40201
   b. 检查 approvalStatus == 1 → 否则抛 40203
   c. 检查是否已是成员 → 抛 40204
   d. 创建 ClubMember，role = MEMBER
   e. 更新 club_info.member_count + 1
   f. 发布 MemberJoinedEvent
```

### 2.4 退出社团流程

```
1. Service.leaveClub(clubId, userId):
   a. 查询 ClubMember → 不存在抛 40205
   b. 检查 role != PRESIDENT → 否则抛 40206
   c. 更新 ClubMember.status = 0（逻辑删除）
   d. 更新 club_info.member_count - 1
   e. 发布 MemberLeftEvent
```

---

## 3. 活动模块（核心）

### 3.1 类设计

```
ActivityController
    ├── POST   /activities                          → createActivity(@Valid ActivityCreateDTO)
    ├── GET    /activities                          → listActivities(ActivityQueryDTO)
    ├── GET    /activities/{id}                     → getActivityDetail(Long id)
    ├── PUT    /activities/{id}                     → updateActivity(Long id, @Valid ActivityUpdateDTO)
    ├── DELETE /activities/{id}                     → deleteActivity(Long id)
    ├── POST   /activities/{id}/submit              → submitForApproval(Long id)
    ├── POST   /activities/{id}/approve             → approveActivity(Long id, @Valid ApprovalDTO)
    ├── PUT    /activities/{id}/status/in-progress  → startActivity(Long id)
    └── PUT    /activities/{id}/complete            → completeActivity(Long id, @Valid CompleteDTO)

ActivityService / ActivityServiceImpl
    ├── createActivity(Long userId, ActivityCreateDTO): ActivityVO
    ├── listActivities(ActivityQueryDTO): PageResult<ActivityVO>
    ├── getActivityDetail(Long id): ActivityDetailVO
    ├── updateActivity(Long id, ActivityUpdateDTO): void
    ├── deleteActivity(Long id): void
    ├── submitForApproval(Long id): void
    ├── approveActivity(Long id, Long approverId, ApprovalDTO): void
    ├── startActivity(Long id): void
    └── completeActivity(Long id, CompleteDTO): void

ActivityStateManager          // 状态机管理（状态模式）
    ├── transition(ActivityStatus from, ActivityStatus to, Activity activity): void
    ├── canTransition(ActivityStatus from, ActivityStatus to): boolean
    └── getAvailableTransitions(ActivityStatus current): List<ActivityStatus>

ApprovalManager               // 审批逻辑（策略模式 + 责任链模式）
    ├── getApprovalStrategy(ActivityType type): ApprovalStrategy
    ├── executeApprovalChain(Activity activity, Long approverId, ApprovalDTO dto): void
    └── checkAllApproved(Activity activity): boolean

ActivityMapper extends BaseMapper<Activity>
ApprovalMapper extends BaseMapper<Approval>
```

### 3.2 状态机设计（状态模式）

```java
// ActivityStateMachine.java
public class ActivityStateMachine {

    // 定义合法的状态转换
    private static final Map<ActivityStatus, Set<ActivityStatus>> TRANSITIONS = Map.of(
        ActivityStatus.DRAFT,      Set.of(PENDING, ARCHIVED),
        ActivityStatus.PENDING,    Set.of(APPROVED, REJECTED),
        ActivityStatus.APPROVED,   Set.of(IN_PROGRESS, REJECTED),
        ActivityStatus.REJECTED,   Set.of(DRAFT),       // 驳回后可重新编辑再提交
        ActivityStatus.IN_PROGRESS, Set.of(COMPLETED),
        ActivityStatus.COMPLETED,  Set.of(ARCHIVED),
        ActivityStatus.ARCHIVED,   Set.of()
    );

    public void transition(Activity activity, ActivityStatus targetStatus) {
        ActivityStatus currentStatus = ActivityStatus.valueOf(activity.getStatus());
        Set<ActivityStatus> allowed = TRANSITIONS.get(currentStatus);
        if (!allowed.contains(targetStatus)) {
            throw new BusinessException(ErrorCode.ACTIVITY_STATUS_INVALID,
                currentStatus + " 不能转换到 " + targetStatus);
        }
        activity.setStatus(targetStatus.name());
        // MyBatis-Plus 乐观锁：version 字段自动 +1
    }

    public boolean canTransition(ActivityStatus from, ActivityStatus to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
}
```

**状态流转图：**
```
DRAFT ──→ PENDING ──→ APPROVED ──→ IN_PROGRESS ──→ COMPLETED ──→ ARCHIVED
  ↑          │            │
  │          │            │
  └──────────┘            │
  (驳回后重新编辑)    REJECTED
```

### 3.3 审批策略（策略模式）

```java
// 审批策略接口
public interface ApprovalStrategy {
    List<ApprovalLevel> getApprovalLevels();  // 需要的审批级别
    boolean isAutoApproved(Activity activity); // 是否自动通过
}

// 校内活动审批策略
@Component
public class CampusApprovalStrategy implements ApprovalStrategy {
    @Override
    public List<ApprovalLevel> getApprovalLevels() {
        return List.of(
            new ApprovalLevel(1, "社长确认"),
            new ApprovalLevel(2, "指导老师审批")
        );
    }
    @Override
    public boolean isAutoApproved(Activity activity) {
        return activity.getBudget().compareTo(new BigDecimal("100")) <= 0; // 100元以下自动通过
    }
}

// 校外活动审批策略
@Component
public class OffCampusApprovalStrategy implements ApprovalStrategy {
    @Override
    public List<ApprovalLevel> getApprovalLevels() {
        return List.of(
            new ApprovalLevel(1, "社长确认"),
            new ApprovalLevel(2, "指导老师审批"),
            new ApprovalLevel(3, "团委审批")
        );
    }
    @Override
    public boolean isAutoApproved(Activity activity) { return false; }
}

// 大型活动审批策略
@Component
public class LargeApprovalStrategy implements ApprovalStrategy {
    @Override
    public List<ApprovalLevel> getApprovalLevels() {
        return List.of(
            new ApprovalLevel(1, "社长确认"),
            new ApprovalLevel(2, "指导老师审批"),
            new ApprovalLevel(3, "团委审批")
        );
    }
    @Override
    public boolean isAutoApproved(Activity activity) { return false; }
}

// 策略工厂
@Component
public class ApprovalStrategyFactory {
    @Autowired private CampusApprovalStrategy campusStrategy;
    @Autowired private OffCampusApprovalStrategy offCampusStrategy;
    @Autowired private LargeApprovalStrategy largeStrategy;

    public ApprovalStrategy getStrategy(ActivityType type) {
        return switch (type) {
            case CAMPUS -> campusStrategy;
            case OFF_CAMPUS -> offCampusStrategy;
            case LARGE -> largeStrategy;
        };
    }
}
```

### 3.4 审批执行流程

```
Service.approveActivity(id, approverId, dto):
    1. 查询 Activity → 不存在抛 40301
    2. 状态机检查：当前状态必须是 PENDING 或 APPROVED（多级审批中间态）→ 否则抛 40302
    3. 获取审批策略：ApprovalStrategyFactory.getStrategy(activity.type)
    4. 检查是否已审批过：查询 act_approval 中该 approverId + activityId → 抛 40304
    5. 保存审批记录 act_approval
    6. 如果 dto.approved == false：
       a. 状态机转换 → REJECTED
       b. 发布 ActivityRejectedEvent
    7. 如果 dto.approved == true：
       a. 检查是否所有级别都已审批通过
       b. 全部通过 → 状态机转换 → APPROVED，发布 ActivityApprovedEvent
       c. 未全部通过 → 保持 PENDING 状态，等待下一级审批
```

### 3.5 活动创建流程

```
Service.createActivity(userId, dto):
    1. 校验 endTime > startTime → 否则抛 40001
    2. 校验用户所属社团 → 无社团则抛 40003
    3. 创建 Activity Entity：
       - status = DRAFT
       - clubId = 用户所属社团
       - createdBy = userId
       - version = 1
    4. 保存 Activity
    5. 返回 ActivityVO
```

---

## 4. 报名签到模块

### 4.1 类设计

```
RegistrationController
    ├── POST   /registrations                          → register(@Valid RegistrationDTO)
    ├── DELETE /registrations/{activityId}              → cancelRegistration(Long activityId)
    ├── GET    /registrations/me                        → myRegistrations(RegistrationQueryDTO)
    └── GET    /activities/{activityId}/registrations   → listRegistrations(Long activityId, RegistrationQueryDTO)

SignInController
    ├── POST /activities/{id}/sign-in/code              → generateSignInCode(Long id)
    ├── POST /sign-in                                   → signIn(@Valid SignInDTO)
    └── GET  /activities/{id}/sign-in/statistics        → getSignInStatistics(Long id)

RegistrationService / RegistrationServiceImpl
    ├── register(Long userId, RegistrationDTO): RegistrationVO
    ├── cancelRegistration(Long activityId, Long userId): void
    ├── myRegistrations(Long userId, RegistrationQueryDTO): PageResult<RegistrationVO>
    └── listRegistrations(Long activityId, RegistrationQueryDTO): PageResult<RegistrationVO>

SignInService / SignInServiceImpl
    ├── generateCode(Long activityId): SignInCodeVO
    ├── signIn(Long userId, SignInDTO): void
    └── getStatistics(Long activityId): SignInStatisticsVO

RegistrationMapper extends BaseMapper<Registration>
SignInMapper extends BaseMapper<SignIn>
```

### 4.2 报名流程（含并发控制）

```
Service.register(userId, dto):
    1. 查询 Activity → 不存在抛 40301
    2. 检查活动状态：必须为 APPROVED → 否则抛 40302
    3. 检查报名截止时间：now < registrationDeadline → 否则抛 40403
    4. 检查是否已报名：查询 registration (activityId + userId) → 存在抛 40402
    5. 并发控制 - 乐观锁：
       a. 查询 activity.currentParticipants 和 activity.maxParticipants
       b. 如果 currentParticipants >= maxParticipants → 抛 40401
       c. UPDATE act_activity
          SET current_participants = current_participants + 1,
              version = version + 1
          WHERE id = ? AND version = ? AND current_participants < max_participants
       d. 如果更新行数为 0 → 抛 40401（名额已满或并发冲突）
    6. 创建 Registration Entity
    7. 保存 Registration
    8. 返回 RegistrationVO
```

### 4.3 签到流程（Redis 去重）

```
SignInService.signIn(userId, dto):
    1. 查询 Activity → 不存在抛 40301
    2. 检查活动状态：必须为 IN_PROGRESS → 否则抛 40302
    3. 校验签到码：Redis GET sign_in_code:{activityId} → 不匹配抛 40406
    4. 检查是否已报名：查询 registration → 不存在抛 40404
    5. Redis 去重：
       a. SISMEMBER sign_in:{activityId} {userId}
       b. 如果已存在 → 抛 40405
    6. 创建 SignIn Entity
    7. 保存 SignIn
    8. Redis SADD sign_in:{activityId} {userId}
```

### 4.4 签到码生成

```
SignInService.generateCode(activityId):
    1. 生成 6 位随机字母数字组合
    2. Redis SET sign_in_code:{activityId} {code} EX 300  (5分钟过期)
    3. 返回 SignInCodeVO（code + expireAt）
```

---

## 5. 资源管理模块

### 5.1 类设计

```
VenueController
    ├── GET  /venues                          → listVenues(VenueQueryDTO)
    └── GET  /venues/{id}/availability        → getAvailability(Long id, String date)

VenueBookingController
    ├── POST   /venue-bookings                → bookVenue(@Valid VenueBookingDTO)
    └── DELETE /venue-bookings/{id}            → cancelBooking(Long id)

EquipmentController
    ├── GET  /equipment                        → listEquipment(EquipmentQueryDTO)

EquipmentBorrowController
    ├── POST /equipment-borrows               → borrowEquipment(@Valid BorrowDTO)
    └── PUT  /equipment-borrows/{id}/return   → returnEquipment(Long id)

VenueService / VenueServiceImpl
    ├── listVenues(VenueQueryDTO): PageResult<VenueVO>
    └── getAvailability(Long venueId, LocalDate date): VenueAvailabilityVO

VenueBookingService / VenueBookingServiceImpl
    ├── bookVenue(VenueBookingDTO): void
    └── cancelBooking(Long bookingId): void

EquipmentService / EquipmentServiceImpl
    └── listEquipment(EquipmentQueryDTO): PageResult<EquipmentVO>

EquipmentBorrowService / EquipmentBorrowServiceImpl
    ├── borrowEquipment(BorrowDTO): void
    └── returnEquipment(Long borrowId): void
```

### 5.2 场地预约流程（冲突检测）

```
VenueBookingService.bookVenue(dto):
    1. 查询 Venue → 不存在抛 40503
    2. 检查时间合理性：endTime > startTime
    3. 冲突检测：查询同一 venue 同一 date 是否有时间段重叠的预约
       SELECT * FROM res_venue_booking
       WHERE venue_id = ? AND booking_date = ?
         AND status = 1
         AND ((start_time < #{endTime} AND end_time > #{startTime}))
       如果存在 → 抛 40502
    4. 创建 VenueBooking Entity
    5. 保存 VenueBooking
```

### 5.3 设备借用流程

```
EquipmentBorrowService.borrowEquipment(dto):
    1. 查询 Equipment → 不存在抛 40504
    2. 检查 availableCount >= borrowCount → 否则抛 40504
    3. 乐观锁更新：
       UPDATE res_equipment
       SET available_count = available_count - #{borrowCount},
           version = version + 1
       WHERE id = ? AND version = ? AND available_count >= #{borrowCount}
       更新行数为 0 → 抛 40504
    4. 创建 EquipmentBorrow Entity
    5. 保存 EquipmentBorrow
```

---

## 6. 通知模块

### 6.1 类设计

```
NotificationController
    ├── GET  /notifications/me                → myNotifications(NotificationQueryDTO)
    ├── PUT  /notifications/{id}/read         → markAsRead(Long id)
    ├── PUT  /notifications/read-all          → markAllAsRead()
    └── GET  /notifications/me/unread-count   → getUnreadCount()

NotificationService / NotificationServiceImpl
    ├── myNotifications(Long userId, NotificationQueryDTO): PageResult<NotificationVO>
    ├── markAsRead(Long notificationId, Long userId): void
    ├── markAllAsRead(Long userId): void
    ├── getUnreadCount(Long userId): Integer
    └── sendNotification(Long userId, String title, String content, NotificationType type, Long relatedId): void

NotificationManager                  // 模板方法模式
    ├── sendSystemNotification(...)
    ├── sendActivityNotification(...)
    └── sendApprovalNotification(...)
    // 模板方法：buildContent() → selectChannel() → doSend()

ActivityEventListener               // 观察者模式 - 事件监听
    ├── onActivityApproved(ActivityApprovedEvent event)
    ├── onActivityRejected(ActivityRejectedEvent event)
    ├── onActivityCompleted(ActivityCompletedEvent event)
    ├── onMemberJoined(MemberJoinedEvent event)
    └── onRegistrationFull(RegistrationFullEvent event)
```

### 6.2 模板方法模式

```java
public abstract class NotificationManager {

    // 模板方法 - 固定流程
    public final void send(Long userId, String title, Long relatedId) {
        String content = buildContent(userId, title, relatedId);
        String channel = selectChannel(userId);
        doSend(userId, title, content, channel);
    }

    // 子类实现 - 构建内容
    protected abstract String buildContent(Long userId, String title, Long relatedId);

    // 子类实现 - 选择渠道
    protected String selectChannel(Long userId) {
        return "SYSTEM";  // 默认站内通知
    }

    // 实际发送
    private void doSend(Long userId, String title, String content, String channel) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(channel);
        notification.setIsRead(0);
        notificationMapper.insert(notification);
    }
}
```

### 6.3 事件监听器（观察者模式）

```java
@Component
public class ActivityEventListener {

    @Autowired private NotificationService notificationService;
    @Autowired private StatisticsService statisticsService;

    @Async
    @EventListener
    public void onActivityApproved(ActivityApprovedEvent event) {
        // 通知活动创建者
        Activity activity = activityMapper.selectById(event.getActivityId());
        notificationService.sendNotification(
            activity.getCreatedBy(),
            "活动审批通过",
            "您创建的活动「" + activity.getTitle() + "」已通过审批",
            NotificationType.APPROVAL,
            event.getActivityId()
        );
    }

    @Async
    @EventListener
    public void onActivityCompleted(ActivityCompletedEvent event) {
        // 更新统计
        statisticsService.updateActivityStats(event.getActivityId());
        // 通知参与者填写反馈
        notificationService.sendNotification(
            // ... 通知所有报名者
        );
    }

    @Async
    @EventListener
    public void onRegistrationFull(RegistrationFullEvent event) {
        // 通知社团负责人
        Activity activity = activityMapper.selectById(event.getActivityId());
        notificationService.sendNotification(
            activity.getCreatedBy(),
            "报名已满",
            "活动「" + activity.getTitle() + "」报名人数已达上限",
            NotificationType.ACTIVITY,
            event.getActivityId()
        );
    }
}
```

---

## 7. 统计模块

### 7.1 类设计

```
StatisticsController
    ├── GET /statistics/clubs/{clubId}/activities  → getClubActivityStats(Long clubId, Integer year)
    ├── GET /statistics/clubs/score                → getClubScores(Integer year)
    └── GET /statistics/overview                   → getOverview()

StatisticsService / StatisticsServiceImpl
    ├── getClubActivityStats(Long clubId, Integer year): ClubActivityStatsVO
    ├── getClubScores(Integer year): List<ClubScoreVO>
    ├── getOverview(): OverviewVO
    └── updateActivityStats(Long activityId): void  // 由事件触发
```

### 7.2 社团考核评分算法

```
评分维度：
- 活动频次分（40%）：年度活动数量 / 基准数量 * 40
- 参与率分（30%）：平均参与率 * 30
- 活动多样性（20%）：不同活动类型数量 / 3 * 20
- 成员活跃度（10%）：活跃成员数 / 总成员数 * 10

总分 = 活动频次分 + 参与率分 + 活动多样性分 + 成员活跃度分
```

---

## 8. 公共组件设计

### 8.1 多租户拦截器

```java
@Component
public class TenantInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long userId = SecurityUtil.getCurrentUserId();
        // 查询用户当前操作的社团 ID
        Long clubId = clubMemberMapper.getClubIdByUserId(userId);
        TenantContext.setClubId(clubId);
        return true;
    }

    @Override
    public void afterCompletion(...) {
        TenantContext.clear();  // 防止内存泄漏
    }
}

// ThreadLocal 存储
public class TenantContext {
    private static final ThreadLocal<Long> CLUB_ID = new ThreadLocal<>();
    public static void setClubId(Long clubId) { CLUB_ID.set(clubId); }
    public static Long getClubId() { return CLUB_ID.get(); }
    public static void clear() { CLUB_ID.remove(); }
}
```

### 8.2 MyBatis-Plus 租户插件配置

```java
@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 租户插件
        TenantLineInnerInterceptor tenantInterceptor = new TenantLineInnerInterceptor();
        tenantInterceptor.setTenantLineHandler(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long clubId = TenantContext.getClubId();
                return clubId != null ? new LongValue(clubId) : null;
            }
            @Override
            public String getTenantIdColumn() { return "club_id"; }
            @Override
            public boolean ignoreTable(String tableName) {
                // 系统表不做租户隔离
                return tableName.startsWith("sys_")
                    || tableName.equals("operation_log")
                    || TenantContext.getClubId() == null;
            }
        });
        interceptor.addInnerInterceptor(tenantInterceptor);

        // 分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        // 乐观锁插件
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        return interceptor;
    }
}
```
