package com.habitforge.modules.contract.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改契约（null = 不改）
 */
@Data
public class ContractUpdateRequest {

    @Size(max = 100, message = "伙伴姓名最长 100 字")
    private String partnerName;

    @Size(max = 500, message = "违约代价最长 500 字")
    private String penalty;

    private Boolean isPublic;

    @Pattern(regexp = "^(ACTIVE|COMPLETED|BROKEN)$", message = "状态取值不合法")
    private String status;
}
