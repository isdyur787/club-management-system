package com.club.management.club.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.club.management.club.entity.ClubMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ClubMemberMapper extends BaseMapper<ClubMember> {

    @Select("SELECT club_id FROM club_member WHERE user_id = #{userId} AND status = 1 AND deleted = 0 LIMIT 1")
    Long getClubIdByUserId(@Param("userId") Long userId);

    @Select("SELECT club_id FROM club_member WHERE user_id = #{userId} AND status = 1 AND deleted = 0")
    List<Long> getClubIdsByUserId(@Param("userId") Long userId);
}
