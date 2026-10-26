package com.club.management.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.club.management.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
