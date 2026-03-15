import { apiGet, apiPut, apiDelete } from './client'

export interface IntegrationConfig {
  id: number
  type: 'RAINDROP'
  config: string
  enabled: boolean
}

export interface RaindropConfig {
  apiToken: string
  collectionId: number
}

export const integrationsApi = {
  getAll: () => apiGet<IntegrationConfig[]>('/integrations'),
  upsertRaindrop: (config: RaindropConfig) =>
    apiPut<IntegrationConfig>('/integrations/raindrop', config),
  deleteRaindrop: () => apiDelete('/integrations/raindrop'),
}
