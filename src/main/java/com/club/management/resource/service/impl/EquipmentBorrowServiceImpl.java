package com.club.management.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.club.management.club.mapper.ClubMemberMapper;
import com.club.management.common.enums.BorrowStatus;
import com.club.management.common.exception.BusinessException;
import com.club.management.common.exception.ErrorCode;
import com.club.management.common.exception.ForbiddenException;
import com.club.management.common.exception.NotFoundException;
import com.club.management.resource.dto.BorrowDTO;
import com.club.management.resource.entity.Equipment;
import com.club.management.resource.entity.EquipmentBorrow;
import com.club.management.resource.mapper.EquipmentBorrowMapper;
import com.club.management.resource.mapper.EquipmentMapper;
import com.club.management.resource.service.EquipmentBorrowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class EquipmentBorrowServiceImpl implements EquipmentBorrowService {

    @Autowired
    private EquipmentMapper equipmentMapper;

    @Autowired
    private EquipmentBorrowMapper equipmentBorrowMapper;

    @Autowired
    private ClubMemberMapper clubMemberMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void borrowEquipment(Long userId, BorrowDTO dto) {
        Equipment equipment = equipmentMapper.selectById(dto.getEquipmentId());
        if (equipment == null) {
            throw new NotFoundException(ErrorCode.EQUIPMENT_NOT_AVAILABLE);
        }

        // 乐观锁更新可用数量
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Equipment> updateWrapper =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        updateWrapper.eq("id", dto.getEquipmentId())
                .eq("version", equipment.getVersion())
                .apply("available_count >= ?", dto.getBorrowCount())
                .set("available_count", equipment.getAvailableCount() - dto.getBorrowCount())
                .set("version", equipment.getVersion() + 1);
        int updated = equipmentMapper.update(null, updateWrapper);

        if (updated == 0) {
            throw new BusinessException(ErrorCode.EQUIPMENT_NOT_AVAILABLE, "设备数量不足");
        }

        Long clubId = clubMemberMapper.getClubIdByUserId(userId);
        if (clubId == null) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }

        EquipmentBorrow borrow = new EquipmentBorrow();
        borrow.setEquipmentId(dto.getEquipmentId());
        borrow.setActivityId(dto.getActivityId());
        borrow.setClubId(clubId);
        borrow.setBorrowerId(userId);
        borrow.setBorrowCount(dto.getBorrowCount());
        borrow.setBorrowTime(LocalDateTime.now());
        borrow.setStatus(BorrowStatus.PENDING.getCode());
        equipmentBorrowMapper.insert(borrow);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnEquipment(Long borrowId) {
        EquipmentBorrow borrow = equipmentBorrowMapper.selectById(borrowId);
        if (borrow == null) {
            throw new NotFoundException(ErrorCode.RESOURCE_NOT_AVAILABLE);
        }

        borrow.setStatus(BorrowStatus.RETURNED.getCode());
        borrow.setReturnTime(LocalDateTime.now());
        equipmentBorrowMapper.updateById(borrow);

        // 原子更新设备可用数量（避免并发丢失）
        equipmentMapper.update(null, new LambdaUpdateWrapper<Equipment>()
                .eq(Equipment::getId, borrow.getEquipmentId())
                .setSql("available_count = available_count + " + borrow.getBorrowCount())
                .setSql("version = version + 1"));
    }
}
