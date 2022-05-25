
export interface ID {
  id: string;
}

export interface UserGroup extends ID {
  name: string;
  dataset: Dataset
  users: Page<User>
}

export interface PageArgs {
  cursor?: string;
  limit?: number;
  filter?: any;
}

export interface User extends ID {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  platformManager: boolean;
  hideOrigin: boolean;
  membership: Membership
  memberships: Page<Membership>
  usageLimit: UsageLimit;
  usageLimitAssigned: boolean;
  usageRemaining: UsageLimitValues;
}

export interface Membership {
  dataset: Dataset
  roles: Role[];
  aggregatedGrant: Grant
}

export interface TeamUser {
  manager: boolean;
  user: User;
}

export interface TeamTiles {
  members: number;
  clients: number;
  datasets: number;
  exports: number;
  streams: number;
}

export interface GraphQ {
  query: string;
  variables?: any;
}

export interface Origin {
  /** Use `__typename` to differentiate */
  producer: Client | User;
  started: number;
  lastUpdate: number;
}

export interface Dataset extends ID {
  name: string;
  description: string;
  properties: any;
  metaStats: MetaStats;
  grants: Grant[];
  accessRequests: AccessRequest[];
  published: boolean;
  openData: boolean;
  archived: boolean;
  invite: Invite;
  metrics: Page<Metric>;
  members: Page<User>;
  keywords: string[];
  license: string;
  contactPoint: string;
  publisher: Publisher;
}

export interface Publisher {
  name: string;
  homepage: string;
}

export interface UpdateDatasetInput {
  name?: string;
  description?: string;
  properties?: any;
  published?: boolean;
  openData?: boolean;
  keywords?: string[];
  license?: string;
  contactPoint?: string;
  publisher?: Publisher;
}

export interface MetaStats {
  lastUpdate: number;
  nrOfMetrics: number;
  nrOfEvents: number;
  nrOfEventsProjection: number;
  approxSizeBytes: number;
  approxSizeBytesProjection: number;
  dataStreams: number;
  ingestApiRequestRate: TimedValue[];
  ingestedEventsRate: TimedValue[];
  eventsQueryApiRequestRate: TimedValue[];
  statsQueryApiRequestRate: TimedValue[];
  consumedEventsRate: TimedValue[];
  queriesConsumedEventsRate: TimedValue[];
  streamingConsumedEventsRate: TimedValue[];
  activeStreams: TimedValue[];
}

export interface AccessRequest {
  id: string;
  timestamp: number;
  user: User;
  team?: Team;
  dataset: Dataset;
  type: Permission[];
  message: string;
  status: AccessRequestStatus;
}

export type AccessRequestStatus = 'PENDING' | 'APPROVED' | 'DENIED';

export interface Grant {
  permissions: Permission[];
  readFilter: any;
}

export interface AuthSubject extends ID {
}

export const Permission_ALL = ['READ', 'WRITE', 'MANAGE'] as const;
export type PermissionTuple = typeof Permission_ALL;
export type Permission = PermissionTuple[-1];

export interface Response<T> {
  responseCode: ResponseCode;
  message: string;
  item: T;
  errors?: GraphQLError[];
}

export type ResponseCode =
  'SUCCESS' | 'BAD_REQUEST' | 'NOT_FOUND' | 'NOT_AUTHORIZED' | 'SERVER_ERROR';

export interface Page<T> {
  items: T[];
  cursor: string;
  /**
   * Not all Page<T> have a count option!
   */
  count?: number;
}

export interface Metric extends ID {
  lastUpdate: number;
  properties: any;
  started: number;
  // schema: any;
  things: Thing[]
}

export interface Thing extends ID {
  properties: any;
  metrics: Metric[];
  started: number;
  lastUpdate: number;
}

export interface Client extends ID {
  user: User;
  name: string;
  team?: Team;
  confidential: boolean;
  onBehalfOfUser: boolean;
  properties: any;
  restrictions: Restriction[];
  scope: Permission[];
  redirectURIs: string[];
  secret?: string;
  usageLimit: UsageLimit;
  usageLimitAssigned: boolean;
  usageRemaining: UsageLimitValues;
}

export interface ClientInput {
  name: string;
  confidential: boolean;
  onBehalfOfUser: boolean;
  properties: any;
  restrictions: RestrictionInput[];
  scope: Permission[];
  redirectURIs: string[];
}

export interface Restriction {
  dataset: Dataset;
  permissions: Permission[];
}

export interface RestrictionInput {
  datasetId: string;
  permissions: Permission[];
}

export interface Invite extends ID {
  roles: Role[];
  expiresInMs: number;
  disallowTeams: boolean;
}

export interface Page<T> {
  items: T[];
  cursor: string;
}

export type ResourceType = 'banner' | 'thumbnail' | 'readme';

export interface ExportInput {
  name: string;
  dataRange: DataRangeInput;
  timestampPrecision: TimestampPrecision;
  fields: EventField[];
  filter: any;
  from: number;
  to: number;
  limit: number;
}

export interface Export {
  id: string;
  name: string;
  dataRange: DataRange;
  timestampPrecision: TimestampPrecision;
  fields: EventField[];
  filter: any;
  from: number;
  to: number;
  requestedOn: number;
  status: DataExportStatus;
  result: DataExportResult;
}

export interface DataExportStatus {
  status: ExportStatus;
  recordsEstimate: number;
  recordsProcessed: number;
}

export interface DataExportResult {
  completedOn: number;
  expiresOn: number;
  sizeInBytes: number;
  compressedSizeInBytes: number;
}


export interface DataStream {
  id: string;
  name: string;
  user: User;
  team: Team;
  dataRange: DataRange;
  timestampPrecision: TimestampPrecision;
  fields: EventField[];
  filter: any
  clientConnected: boolean;
}

export interface CreateStreamInput {
  name: string;
  dataRange: DataRangeInput;
  timestampPrecision: TimestampPrecision;
  fields: EventField[];
  filter: any
}

export const TimestampPrecision_ALL = ['SECONDS', 'MILLISECONDS', 'MICROSECONDS'] as const;
export type TimestampPrecisionTuple = typeof TimestampPrecision_ALL;
export type TimestampPrecision = TimestampPrecisionTuple[-1];

export const EventField_ALL = ['timestamp', 'dataset', 'metric', 'producer', 'source', 'value', 'tags', 'location', 'geohash', 'elevation', 'tsReceived'] as const;
export type EventFieldTuple = typeof EventField_ALL;
export type EventField = EventFieldTuple[-1];

export type ExportStatus = 'QUEUING' | 'GENERATING' | 'CANCELLED' | 'COMPLETED' | 'FAILED';


export interface DataRangeInput {
  datasets: string[];
  metrics: string[];
}

export interface DataRange {
  datasets: Dataset[];
  metrics: string[]
}

export interface UsageLimit {
  id: string;
  name: string;
  description: string;
  defaultLimit: boolean;
  values: UsageLimitValues;
}

export interface UsageLimitValues {
  maxHourlyPrimitiveEventsStored: number;
  maxHourlyComplexEventsStored: number;
  maxHourlyPrimitiveEventsStreamed: number;
  maxHourlyComplexEventsStreamed: number;
  maxHourlyPrimitiveEventQueries: number;
  maxHourlyComplexEventQueries: number;
  maxHourlyPrimitiveStatsQueries: number;
  maxHourlyComplexStatsQueries: number;
  maxDataExports: number;
  maxDataExportRecords: number;
  maxDataStreams: number;
}

export type UsageLimitValue =
  'maxHourlyPrimitiveEventsStored' |
  'maxHourlyComplexEventsStored' |
  'maxHourlyPrimitiveEventsStreamed' |
  'maxHourlyComplexEventsStreamed' |
  'maxHourlyPrimitiveEventQueries' |
  'maxHourlyComplexEventQueries' |
  'maxHourlyPrimitiveStatsQueries' |
  'maxHourlyComplexStatsQueries' |
  'maxDataExports' |
  'maxDataExportRecords' |
  'maxDataStreams';

export interface UsageLimitInput {
  name: string;
  description: string;
  values: UsageLimitValues;
}

export interface UsageLimitDetails {
  usageLimit: UsageLimit
  usageRemaining: UsageLimitValues
}

export interface AggregatedUsageLimitDetails {
  aggregatedUsageLimit: UsageLimit
  usageRemaining: UsageLimitValues
}

export interface UsagePlanDetails {
  usagePlan: UsagePlan;
  usersRemaining: number;
  clientsRemaining: number;
}

export interface UsagePlan {
  id: string;
  name: string;
  description: string;
  defaultPlan: boolean;
  maxUsers: number;
  userUsageLimit: UsageLimit;
  userUsageLimitAssigned: boolean;
  maxClients: number;
  clientUsageLimit: UsageLimit;
  clientUsageLimitAssigned: boolean;
}

export interface UsagePlanInput {
  name: string;
  description: string;
  maxUsers: number;
  userUsageLimitId: string;
  maxClients: number;
  clientUsageLimitId: string;
}

export interface Team {
  id: string;
  name: string;
  description: string;
  usagePlan: UsagePlan;
  usagePlanAssigned: boolean;
  invite: TeamInvite;
  user: TeamUser;
  membership: Membership;
  memberships: Page<Membership>;
  usersRemaining: number;
  clientsRemaining: number;
}

export interface GraphQLErrorResult {
  errors: GraphQLError[];
}

export function isGraphQLErrorResult(object: any): object is GraphQLErrorResult {
  return 'errors' in object;
}

export interface GraphQLError {
  extensions: {
    classification: string
  };
  locations: { line: number, column: number }[];
  message: string;
  path: string[];
  hide?: boolean;
}

export interface TeamInput {
  name: string;
  description?: string;
}

export interface TeamInvite {
  id: string;
  expiresInMs: number;
}

export interface DatasetInvite {
  id: string;
  expiresInMs: number;
}

export interface Role {
  id: string;
  name: string;
  description: string;
  dataset: Dataset;
  user: User;
  team: Team;
  grant: Grant
}

export interface GlobalMetaStats {
  nrOfDatasets: number;
  nrOfMetrics: number;
  nrOfUsers: number;
  nrOfClients: number;
  nrOfEvents: number;
  nrOfEventsProjection: number;
  totalSizeBytes: number;
  totalSizeBytesProjection: number;
  ingestedEventsRate: TimedValue[];
  consumedEventsRate: TimedValue[];
}

export interface TimeSeries {
  values: TimedValue[];
  label: string
}

export interface TimedValue {
  timestamp: number;
  value: number;
}

export interface Announcement {
  id: string;
  timestamp: number;
  title: string;
  content: string;
}

export interface UpdateAnnouncement {
  title: string;
  content: string;
}

export interface ServiceStatus {
  componentId: string;
  windowDurationMs: number;
  groupByMs: number;
  history: Status[]
}

export type Status = 'HEALTHY' | 'UNKNOWN' | 'FAILED' | 'DEGRADED';

export interface ResponseHandler<T> {
  success: (item: T) => void,
  badRequest?: (response: Response<T>) => void,
  error?: (response: Response<T>) => void
}


export interface IssueInput {
  summary: string;
  description: string;
}

export interface Issue {
  _id: string;
  createdAt: number;
  modifiedAt: number;
  summary: string;
  description: string;
  reporter: UserRef;
  assignee: UserRef;
  state: IssueState
}

export interface UserRef {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
}

export interface IssueActivity {
  _id: string;
  createdAt: number;
  modifiedAt: number;
  internal: boolean;
  author: UserRef;
  changeState: IssueState,
  comment: string;
}

export interface IssueOptions {
  status?: IssueState,
  hideClosed?: boolean,
  containsText?: string,
  sort?: 'created_at_asc' | 'created_at_desc' | 'modified_at_asc' | 'modified_at_desc',
}

export interface IssueComment {
  comment: string;
}

export interface IssueUpdate {
  summary?: string;
  description?: string;
  assignee?: string;
}

export interface DataRemovalRequestInput {
  dataRange: DataRangeInput;
  filter: any;
  from: number;
  to: number;
}

export type IssueState = 'WAITING_FOR_SUPPORT' | 'WAITING_FOR_REPORTER' | 'IN_PROGRESS' | 'CANCELLED' | 'RESOLVED';

export interface DatasetProjection {
  nrOfEvents: number;
  nrOfEventsProjection: number;
  approxSizeBytes: number
  approxSizeBytesProjection: number
}

export type AdvancedStatus = AdvancedStatusService | AdvancedStatusStreamer;

export interface AdvancedStatusService {
  component: string;
  lastCallSucceeded: boolean;
  callSuccessRate: number;
  meanRTT: number;
  maxRTT: number;
  minRTT: number;
  persistedRate?: number;
  'fetchedRecords#'?: number;
}

export interface AdvancedStatusStreamer {
  component: string;
  lastCallSucceeded: boolean;
  lastEventThrough: string;
  connectionDropouts: number;
  successRate: number;
  meanLagMs: number;
  maxLagMs: number;
  minLagMs: number;
}

export const FilterExpressionSchema = {
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "oneOf": [
    {
      "properties": {
        "_and": {
          "type": "array"
        }
      },
      "required": [
        "_and"
      ]
    },
    {
      "properties": {
        "_or": {
          "type": "array"
        }
      },
      "required": [
        "_or"
      ]
    },
    {
      "properties": {
        "_not": {
          "type": "object"
        }
      },
      "required": [
        "_not"
      ]
    },
    {
      "properties": {
        "_exists": {
          "type": "string"
        }
      },
      "required": [
        "_exists"
      ]
    },
    {
      "properties": {
        "_withTag": {
          "type": "string"
        }
      },
      "required": [
        "_withTag"
      ]
    },
    {
      "properties": {
        "_withAnyTag": {
          "type": "array"
        }
      },
      "required": [
        "_withAnyTag"
      ]
    },
    {
      "properties": {
        "_locationInCircle": {
          "type": "object",
          "properties": {
            "center": {
              "type": "object",
              "properties": {
                "lat": {
                  "type": "number"
                },
                "lng": {
                  "type": "number"
                }
              }
            },
            "radius": {
              "type": "number"
            }
          }
        }
      },
      "required": [
        "_locationInCircle"
      ]
    },
    {
      "properties": {
        "_locationInPolygon": {
          "type": "array"
        }
      },
      "required": [
        "_locationInPolygon"
      ]
    },
    {
      "patternProperties": {
        "(:?)": {
          "type": "object",

          "oneOf": [
            {
              "properties": {
                "_eq": {
                  "type": "string"
                }
              },
              "required": [
                "_eq"
              ]
            },
            {
              "properties": {
                "_neq": {
                  "type": "string"
                }
              },
              "required": [
                "_neq"
              ]
            },
            {
              "properties": {
                "_gt": {
                  "type": "string"
                }
              },
              "required": [
                "_gt"
              ]
            },
            {
              "properties": {
                "_gte": {
                  "type": "string"
                }
              },
              "required": [
                "_gte"
              ]
            },
            {
              "properties": {
                "_lt": {
                  "type": "string"
                }
              },
              "required": [
                "_lt"
              ]
            },
            {
              "properties": {
                "_lte": {
                  "type": "string"
                }
              },
              "required": [
                "_lte"
              ]
            },
            {
              "properties": {
                "_in": {
                  "type": "array"
                }
              },
              "required": [
                "_in"
              ]
            },
            {
              "properties": {
                "_startsWith": {
                  "type": "string"
                }
              },
              "required": [
                "_startsWith"
              ]
            },
            {
              "properties": {
                "_regex": {
                  "type": "string"
                },
                "_options": {
                  "type": "string"
                }
              },
              "required": [
                "_regex"
              ]
            }
          ]
        }
      }
    }
  ]
};