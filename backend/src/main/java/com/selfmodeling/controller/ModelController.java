package com.selfmodeling.controller;

import com.selfmodeling.dto.PageResult;
import com.selfmodeling.dto.Result;
import com.selfmodeling.dto.StepTreeResult;
import com.selfmodeling.entity.ModelInfo;
import com.selfmodeling.entity.ModelStep;
import com.selfmodeling.request.InsertStepRequest;
import com.selfmodeling.service.ModelService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ModelController {

    @Autowired
    private ModelService modelService;

    // ===== Model Info APIs =====

    @GetMapping("/models")
    public Result<PageResult<ModelInfo>> pageModels(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String modelName,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate) {
        PageResult<ModelInfo> result = modelService.pageModels(pageNum, pageSize, modelName, status, startDate, endDate);
        return Result.success(result);
    }

    @GetMapping("/models/{id}")
    public Result<ModelInfo> getModelDetail(@PathVariable Long id) {
        ModelInfo modelInfo = modelService.getModelDetail(id);
        return Result.success(modelInfo);
    }

    @PostMapping("/models")
    public Result<ModelInfo> createModel(@RequestBody ModelInfo modelInfo) {
        ModelInfo created = modelService.createModel(modelInfo);
        return Result.success("创建成功", created);
    }

    @PutMapping("/models/{id}")
    public Result<Void> updateModel(@PathVariable Long id, @RequestBody ModelInfo modelInfo) {
        modelService.updateModel(id, modelInfo);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/models/{id}")
    public Result<Void> deleteModel(@PathVariable Long id) {
        modelService.deleteModel(id);
        return Result.success("删除成功", null);
    }

    @PatchMapping("/models/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody java.util.Map<String, Integer> body) {
        modelService.updateStatus(id, body.get("status"));
        return Result.success("状态更新成功", null);
    }

    @PostMapping("/models/{id}/copy")
    public Result<ModelInfo> copyModel(@PathVariable Long id) {
        ModelInfo copied = modelService.copyModel(id);
        return Result.success("复制成功", copied);
    }

    // ===== Step APIs =====

    @GetMapping("/models/{modelId}/steps")
    public Result<List<ModelStep>> getSteps(@PathVariable Long modelId) {
        List<ModelStep> steps = modelService.getStepsByModelId(modelId);
        return Result.success(steps);
    }

    @GetMapping("/models/{modelId}/steps/{stepId}")
    public Result<ModelStep> getStepDetail(@PathVariable Long modelId, @PathVariable Long stepId) {
        ModelStep step = modelService.getStepDetail(modelId, stepId);
        return Result.success(step);
    }

    @PostMapping("/models/{modelId}/steps")
    public Result<ModelStep> addStep(@PathVariable Long modelId, @RequestBody ModelStep step) {
        ModelStep created = modelService.addStep(modelId, step);
        return Result.success("添加成功", created);
    }

    @PostMapping("/models/{modelId}/steps/insert")
    public Result<ModelStep> insertStep(@PathVariable Long modelId, @Valid @RequestBody InsertStepRequest request) {
        ModelStep created = modelService.insertStep(modelId, request);
        return Result.success("插入成功", created);
    }

    @PutMapping("/models/{modelId}/steps/{stepId}")
    public Result<Void> updateStep(@PathVariable Long modelId, @PathVariable Long stepId, @RequestBody ModelStep step) {
        modelService.updateStep(modelId, stepId, step);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/models/{modelId}/steps/{stepId}")
    public Result<Void> deleteStep(@PathVariable Long modelId, @PathVariable Long stepId) {
        modelService.deleteStep(modelId, stepId);
        return Result.success("删除成功", null);
    }

    @PatchMapping("/models/{modelId}/steps/{stepId}/reorder")
    public Result<Void> reorderStep(@PathVariable Long modelId, @PathVariable Long stepId,
                                    @RequestBody java.util.Map<String, Long> body) {
        Long targetAfterStepId = body.get("targetAfterStepId");
        modelService.reorderStep(modelId, stepId, targetAfterStepId);
        return Result.success("重排成功", null);
    }

    @PatchMapping("/models/{modelId}/steps/{stepId}/swap")
    public Result<Void> swapSteps(@PathVariable Long modelId, @PathVariable Long stepId,
                                  @RequestBody java.util.Map<String, Long> body) {
        Long swapWithStepId = body.get("swapWithStepId");
        modelService.swapSteps(modelId, stepId, swapWithStepId);
        return Result.success("交换成功", null);
    }

    @GetMapping("/models/{modelId}/steps/tree")
    public Result<StepTreeResult> getStepTree(@PathVariable Long modelId) {
        StepTreeResult tree = modelService.getStepTree(modelId);
        return Result.success(tree);
    }

    @PostMapping("/models/{modelId}/steps/{stepId}/execute")
    public Result<String> executeStep(@PathVariable Long modelId, @PathVariable Long stepId) {
        String result = modelService.executeStep(modelId, stepId);
        return Result.success("执行成功", result);
    }

    @GetMapping("/models/{modelId}/steps/{stepId}/result")
    public Result<PageResult<Map<String, Object>>> getStepResult(
            @PathVariable Long modelId,
            @PathVariable Long stepId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResult<Map<String, Object>> result = modelService.getStepResult(modelId, stepId, pageNum, pageSize);
        return Result.success(result);
    }
}
