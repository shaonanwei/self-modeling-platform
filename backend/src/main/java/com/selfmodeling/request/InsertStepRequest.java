package com.selfmodeling.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.Map;

@Data
public class InsertStepRequest {
    private Long afterStepId;

    @NotBlank(message = "步骤名称不能为空")
    private String stepName;

    @NotBlank(message = "步骤类型不能为空")
    private String stepType;

    private String stepCode;
    private String stepDesc;
    private Integer timeoutSeconds;
    private Integer retryCount;
    private Map<String, Object> stepConfig;
}
