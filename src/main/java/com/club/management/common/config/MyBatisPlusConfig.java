package com.club.management.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.club.management.common.util.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        TenantLineInnerInterceptor tenantInterceptor = new TenantLineInnerInterceptor();
        tenantInterceptor.setTenantLineHandler(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long clubId = TenantContext.getClubId();
                return clubId != null ? new LongValue(clubId) : null;
            }

            @Override
            public String getTenantIdColumn() {
                return "club_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return tableName.startsWith("sys_")
                        || tableName.equals("operation_log")
                        || tableName.equals("res_venue")
                        || tableName.equals("res_equipment")
                        || tableName.equals("noti_notification")
                        || tableName.equals("club_info")
                        || tableName.equals("act_approval")
                        || tableName.equals("reg_registration")
                        || tableName.equals("reg_sign_in")
                        || tableName.equals("act_sign_out")
                        || TenantContext.getClubId() == null;
            }
        });
        interceptor.addInnerInterceptor(tenantInterceptor);

        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        return interceptor;
    }
}
