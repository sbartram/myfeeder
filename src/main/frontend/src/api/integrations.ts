import { apiGet, apiPut, apiDelete } from './client'

export interface IntegrationConfig {
  id: number
  type: 'RAINDROP'
  config: string
  enabled: boolean
}

export interface RaindropConfig {
  collectionId: number
}

export interface RaindropCollection {
  id: number
  title: string
}

export interface RaindropStatus {
  configured: boolean
}

export const integrationsApi = {
  getAll: () => apiGet<IntegrationConfig[]>('/integrations'),
  getRaindropStatus: () => apiGet<RaindropStatus>('/integrations/raindrop/status'),
  listRaindropCollections: () =>
    apiGet<RaindropCollection[]>('/integrations/raindrop/collections'),
  upsertRaindrop: (config: RaindropConfig) =>
    apiPut<IntegrationConfig>('/integrations/raindrop', config),
  deleteRaindrop: () => apiDelete('/integrations/raindrop'),
}
