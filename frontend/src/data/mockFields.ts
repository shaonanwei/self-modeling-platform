/**
 * 模拟字段元数据
 * 集中管理表字段信息，供多个组件复用
 * TODO: 替换为后端 API 获取真实元数据
 */

export interface FieldMeta {
  name: string
  type: string
  comment: string
  selected?: boolean
  alias?: string
}

export const MOCK_FIELDS: Record<string, FieldMeta[]> = {
  user: [
    { name: 'id', type: 'BIGINT', comment: '主键' },
    { name: 'username', type: 'VARCHAR', comment: '用户名' },
    { name: 'email', type: 'VARCHAR', comment: '邮箱' },
    { name: 'phone', type: 'VARCHAR', comment: '手机号' },
    { name: 'status', type: 'INT', comment: '状态' },
    { name: 'create_time', type: 'TIMESTAMP', comment: '创建时间' }
  ],
  order: [
    { name: 'id', type: 'BIGINT', comment: '主键' },
    { name: 'user_id', type: 'BIGINT', comment: '用户ID' },
    { name: 'product_id', type: 'BIGINT', comment: '商品ID' },
    { name: 'quantity', type: 'INT', comment: '数量' },
    { name: 'total_amount', type: 'DECIMAL', comment: '总金额' },
    { name: 'status', type: 'VARCHAR', comment: '订单状态' },
    { name: 'create_time', type: 'TIMESTAMP', comment: '下单时间' }
  ],
  product: [
    { name: 'id', type: 'BIGINT', comment: '主键' },
    { name: 'name', type: 'VARCHAR', comment: '商品名称' },
    { name: 'category_id', type: 'BIGINT', comment: '分类ID' },
    { name: 'price', type: 'DECIMAL', comment: '价格' },
    { name: 'stock', type: 'INT', comment: '库存' }
  ],
  category: [
    { name: 'id', type: 'BIGINT', comment: '主键' },
    { name: 'name', type: 'VARCHAR', comment: '分类名称' },
    { name: 'parent_id', type: 'BIGINT', comment: '父分类ID' }
  ]
}

/** 表名列表（用于表选择） */
export const AVAILABLE_TABLES = [
  { name: 'user', comment: '用户表' },
  { name: 'order', comment: '订单表' },
  { name: 'product', comment: '商品表' },
  { name: 'category', comment: '分类表' }
]

/** 按表名获取字段名列表 */
export function getFieldNames(tableName: string): string[] {
  return (MOCK_FIELDS[tableName] || []).map(f => f.name)
}

/** 按表名获取完整字段定义 */
export function getFields(tableName: string): FieldMeta[] {
  return (MOCK_FIELDS[tableName] || []).map(f => ({ ...f }))
}
