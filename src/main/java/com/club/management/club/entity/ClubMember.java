package com.club.management.club.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.club.management.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("club_member")
public class ClubMember extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long clubId;

    private Long userId;

    private String role;

    private LocalDateTime joinedAt;

    private Integer status;
}
