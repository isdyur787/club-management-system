package com.club.management.checkin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.club.management.checkin.entity.CheckinRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface CheckinRecordMapper extends BaseMapper<CheckinRecord> {

    @Select("SELECT COUNT(*) AS submitted_count, COALESCE(AVG(rating), 0) AS avg_rating " +
            "FROM act_checkin_record WHERE task_id = #{taskId}")
    Map<String, Object> selectTaskStats(@Param("taskId") Long taskId);
}
