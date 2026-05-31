import request from '@/utils/request'
import type { ModelInfo, ModelStep, InsertStepRequest, StepTreeResult, PageResult } from '@/types/model'

export const modelApi = {
  // Model CRUD
  pageModels(params: { pageNum?: number; pageSize?: number; modelName?: string; status?: number }) {
    return request.get<any, { data: PageResult<ModelInfo> }>('/api/v1/models', { params })
  },

  getModel(id: number) {
    return request.get<any, { data: ModelInfo }>(`/api/v1/models/${id}`)
  },

  createModel(data: Partial<ModelInfo>) {
    return request.post<any, { data: ModelInfo }>('/api/v1/models', data)
  },

  updateModel(id: number, data: Partial<ModelInfo>) {
    return request.put<any, void>(`/api/v1/models/${id}`, data)
  },

  deleteModel(id: number) {
    return request.delete<any, void>(`/api/v1/models/${id}`)
  },

  copyModel(id: number) {
    return request.post<any, { data: ModelInfo }>(`/api/v1/models/${id}/copy`)
  },

  updateStatus(id: number, status: number) {
    return request.patch<any, void>(`/api/v1/models/${id}/status`, { status })
  },

  // Step CRUD
  getSteps(modelId: number) {
    return request.get<any, { data: ModelStep[] }>(`/api/v1/models/${modelId}/steps`)
  },

  getStep(modelId: number, stepId: number) {
    return request.get<any, { data: ModelStep }>(`/api/v1/models/${modelId}/steps/${stepId}`)
  },

  addStep(modelId: number, data: Partial<ModelStep>) {
    return request.post<any, { data: ModelStep }>(`/api/v1/models/${modelId}/steps`, data)
  },

  insertStep(modelId: number, data: InsertStepRequest) {
    return request.post<any, { data: ModelStep }>(`/api/v1/models/${modelId}/steps/insert`, data)
  },

  updateStep(modelId: number, stepId: number, data: Partial<ModelStep>) {
    return request.put<any, void>(`/api/v1/models/${modelId}/steps/${stepId}`, data)
  },

  deleteStep(modelId: number, stepId: number) {
    return request.delete<any, void>(`/api/v1/models/${modelId}/steps/${stepId}`)
  },

  reorderStep(modelId: number, stepId: number, targetAfterStepId: number) {
    return request.patch<any, void>(`/api/v1/models/${modelId}/steps/${stepId}/reorder`, { targetAfterStepId })
  },

  swapSteps(modelId: number, stepId: number, swapWithStepId: number) {
    return request.patch<any, void>(`/api/v1/models/${modelId}/steps/${stepId}/swap`, { swapWithStepId })
  },

  getStepTree(modelId: number) {
    return request.get<any, { data: StepTreeResult }>(`/api/v1/models/${modelId}/steps/tree`)
  },

  executeStep(modelId: number, stepId: number) {
    return request.post<any, any>(`/api/v1/models/${modelId}/steps/${stepId}/execute`)
  },

  getStepResult(modelId: number, stepId: number, pageNum: number = 1, pageSize: number = 10) {
    return request.get<any, any>(`/api/v1/models/${modelId}/steps/${stepId}/result?pageNum=${pageNum}&pageSize=${pageSize}`)
  }
}
