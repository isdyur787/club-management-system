package com.club.management.common.config;

import com.club.management.common.util.SecurityUtil;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        try {
            Long userId = SecurityUtil.getCurrentUserId();
            this.strictInsertFill(metaObject, "createdBy", Long.class, userId);
            this.strictInsertFill(metaObject, "updatedBy", Long.class, userId);
        } catch (Exception e) {
            this.strictInsertFill(metaObject, "createdBy", Long.class, null);
            this.strictInsertFill(metaObject, "updatedBy", Long.class, null);
        }
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        try {
            Long userId = SecurityUtil.getCurrentUserId();
            this.strictUpdateFill(metaObject, "updatedBy", Long.class, userId);
        } catch (Exception e) {
            this.strictUpdateFill(metaObject, "updatedBy", Long.class, null);
        }
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
