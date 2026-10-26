package com.club.management.points.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.club.management.points.entity.PointsLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface PointsLogMapper extends BaseMapper<PointsLog> {

    @Select("SELECT user_id, SUM(points) AS total_points FROM member_points_log " +
            "WHERE club_id = #{clubId} GROUP BY user_id ORDER BY total_points DESC LIMIT #{top}")
    List<Map<String, Object>> selectPointsRanking(@Param("clubId") Long clubId, @Param("top") int top);

    @Select("SELECT COALESCE(SUM(points), 0) FROM member_points_log WHERE club_id = #{clubId} AND user_id = #{userId}")
    int selectTotalPoints(@Param("clubId") Long clubId, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) + 1 FROM (" +
            "  SELECT user_id, SUM(points) AS tp FROM member_points_log WHERE club_id = #{clubId} GROUP BY user_id" +
            ") t WHERE t.tp > (" +
            "  SELECT COALESCE(SUM(points), 0) FROM member_points_log WHERE club_id = #{clubId} AND user_id = #{userId}" +
            ")")
    int selectUserRank(@Param("clubId") Long clubId, @Param("userId") Long userId);

    /**
     * 批量插入积分日志
     */
    int batchInsert(@Param("list") List<PointsLog> list);
}
