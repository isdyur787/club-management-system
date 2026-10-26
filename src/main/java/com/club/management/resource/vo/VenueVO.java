package com.club.management.resource.vo;

import lombok.Data;

@Data
public class VenueVO {

    private Long id;
    private String venueName;
    private String location;
    private Integer capacity;
    private String facilities;
    private Integer status;
}
