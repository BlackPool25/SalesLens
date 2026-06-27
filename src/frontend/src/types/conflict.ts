export interface ConflictRecord {
  id: string
  entityType: string
  entityId: string
  fieldName: string
  sourceAId: string
  sourceBId: string
  valueA: any
  valueB: any
  status: 'OPEN' | 'RESOLVED' | 'SUPPRESSED'
  resolutionStrategy: string
  createdAt: string
  // Optional fields that might be returned by the API
  sourceAName?: string
  sourceBName?: string
  sourceATrustScore?: number
  sourceBTrustScore?: number
  resolvedValue?: any
}
