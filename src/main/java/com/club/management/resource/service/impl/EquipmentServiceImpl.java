package com.club.management.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.club.management.common.response.PageResult;
import com.club.management.resource.dto.EquipmentQueryDTO;
import com.club.management.resource.entity.Equipment;
import com.club.management.resource.mapper.EquipmentMapper;
import com.club.management.resource.service.EquipmentService;
import com.club.management.resource.vo.EquipmentVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class EquipmentServiceImpl implements EquipmentService {

    @Autowired
    private EquipmentMapper equipmentMapper;

    @Override
    public PageResult<EquipmentVO> listEquipment(EquipmentQueryDTO dto) {
        LambdaQueryWrapper<Equipment> wrapper = new LambdaQueryWrapper<>();
        if (dto.getEquipmentType() != null) {
            wrapper.eq(Equipment::getEquipmentType, dto.getEquipmentType());
        }
        if (dto.getStatus() != null) {
            wrapper.eq(Equipment::getStatus, dto.getStatus());
        }
        wrapper.orderByAsc(Equipment::getEquipmentName);

        Page<Equipment> page = equipmentMapper.selectPage(
                new Page<>(dto.getPageNum(), dto.getPageSize()), wrapper);

        List<EquipmentVO> voList = page.getRecords().stream().map(eq -> {
            EquipmentVO vo = new EquipmentVO();
            vo.setId(eq.getId());
            vo.setEquipmentName(eq.getEquipmentName());
            vo.setEquipmentType(eq.getEquipmentType());
            vo.setTotalCount(eq.getTotalCount());
            vo.setAvailableCount(eq.getAvailableCount());
            vo.setStatus(eq.getStatus());
            return vo;
        }).collect(Collectors.toList());

        return new PageResult<>(dto.getPageNum(), dto.getPageSize(), page.getTotal(), voList);
    }
}
