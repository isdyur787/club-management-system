package com.club.management.points.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.club.management.points.entity.PointsLog;
import com.club.management.points.vo.PointsClubVO;
import com.club.management.points.vo.PointsSummaryVO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

@Mapper
public interface PointsLogMapper extends BaseMapper<PointsLog> {

    List<Map<String, Object>> selectPointsRanking(@Param("clubId") Long clubId, @Param("top") int top);

    @Select("SELECT COALESCE(SUM(points), 0) FROM member_points_log WHERE club_id = #{clubId} AND user_id = #{userId}")
    int selectTotalPoints(@Param("clubId") Long clubId, @Param("userId") Long userId);

    int selectUserRank(@Param("clubId") Long clubId, @Param("userId") Long userId);

    // 本查询显式限定当前账号的成员关系，不能再叠加默认的首个社团租户条件。
    @InterceptorIgnore(tenantLine = "true")
    List<PointsClubVO> selectAvailableClubs(@Param("userId") Long userId, @Param("allClubs") boolean allClubs);

    Map<String, Object> selectClubOverview(@Param("clubId") Long clubId,
        @Param("monthStart") LocalDateTime monthStart, @Param("monthEnd") LocalDateTime monthEnd);

    List<PointsSummaryVO.DailyPoint> selectDailyPoints(@Param("clubId") Long clubId,
        @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<PointsSummaryVO.SourcePoint> selectSourcePoints(@Param("clubId") Long clubId);

    /**
     * 批量插入积分日志
     */
    int batchInsert(@Param("list") List<PointsLog> list);
}
