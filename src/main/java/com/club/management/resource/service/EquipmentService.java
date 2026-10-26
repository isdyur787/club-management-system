package com.club.management.resource.service;

import com.club.management.common.response.PageResult;
import com.club.management.resource.dto.EquipmentQueryDTO;
import com.club.management.resource.vo.EquipmentVO;

public interface EquipmentService {
    PageResult<EquipmentVO> listEquipment(EquipmentQueryDTO dto);
}
