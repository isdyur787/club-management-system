// 普通请求与 SSE 使用同一个地址，支持独立部署或同源反向代理。
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api/v1').replace(/\/+$/, '')
