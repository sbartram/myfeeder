import { apiGet } from './client'

export interface VersionInfo {
  version: string
  buildTime: string
}

export const versionApi = {
  get: () => apiGet<VersionInfo>('/version'),
}
