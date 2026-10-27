package com.club.management.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.club.management.activity.entity.Activity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface ActivityMapper extends BaseMapper<Activity> {

    // 个人报名可跨社团；通过本人报名关系授权，避免默认社团租户过滤活动详情。
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>" +
            "SELECT a.* FROM act_activity a WHERE a.deleted = 0 AND a.id IN " +
            "<foreach collection='activityIds' item='activityId' open='(' separator=',' close=')'>" +
            "#{activityId}" +
            "</foreach>" +
            " AND EXISTS (SELECT 1 FROM reg_registration r " +
            "WHERE r.activity_id = a.id AND r.user_id = #{userId} AND r.deleted = 0)" +
            "</script>")
    List<Activity> selectRegisteredActivities(@Param("userId") Long userId,
                                              @Param("activityIds") Collection<Long> activityIds);

    @Select("SELECT COUNT(*) AS total_count, COALESCE(SUM(current_participants), 0) AS total_participants " +
            "FROM act_activity WHERE deleted = 0")
    Map<String, Object> selectActivityOverview();

    @Select("SELECT club_id, COUNT(*) AS activity_count, " +
            "COALESCE(SUM(current_participants), 0) AS total_participants, " +
            "AVG(current_participants) AS avg_participants, " +
            "COUNT(DISTINCT type) AS type_count " +
            "FROM act_activity WHERE deleted = 0 " +
            "AND start_time >= #{startTime} AND start_time < #{endTime} " +
            "GROUP BY club_id")
    List<Map<String, Object>> selectClubScoreStats(
            @Param("startTime") java.time.LocalDateTime startTime,
            @Param("endTime") java.time.LocalDateTime endTime);
}
