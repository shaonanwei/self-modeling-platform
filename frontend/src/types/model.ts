export interface UserInfo {
  id: number
  username: string
  nickname: string
  email: string
  phone: string
  avatar: string
  status: number
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  tokenType: string
}

export interface ModelInfo {
  id: number
  modelCode: string
  modelName: string
  modelDesc: string
  modelType: string
  dataSource: string
  status: number
  version: number
  creator: string
  createTime: string
  updateTime: string
}

export interface ModelStep {
  id: number
  modelId: number
  stepCode: string
  stepName: string
  stepDesc: string
  stepType: 'start' | 'end' | 'task' | 'gateway' | 'subprocess'
  sortOrder: number
  stepConfig: string
  resultTableName?: string
  executeStatus?: string
  executeStartTime?: string
  executeEndTime?: string
  executeLog?: string
  conditionExpr?: string
  createTime: string
  updateTime: string
}

export interface InsertStepRequest {
  afterStepId: number
  stepCode?: string
  stepName: string
  stepType: string
  stepDesc?: string
  stepConfig?: Record<string, any>
}

export interface PageResult<T> {
  total: number
  list: T[]
}

export interface StepTreeResult {
  nodes: Array<{
    id: number
    stepName: string
    stepType: string
    x: number
    y: number
    config?: Record<string, any>
  }>
  edges: Array<{
    source: number
    target: number
    label: string
  }>
}
