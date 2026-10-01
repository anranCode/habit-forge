package com.habitforge.modules.contract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建契约
 */
@Data
public class ContractRequest {

    @NotBlank(message = "请选择要约束的习惯")
    private String habitId;

    @NotBlank(message = "请填写问责伙伴")
    @Size(max = 100, message = "伙伴姓名最长 100 字")
    private String partnerName;

    @NotBlank(message = "请填写违约代价")
    @Size(max = 500, message = "违约代价最长 500 字")
    private String penalty;

    private Boolean isPublic;
}
