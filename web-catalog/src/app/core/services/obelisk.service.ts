import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ErrorComponent } from '@shared/modals';
import { AccessRequest, AdvancedStatus, AggregatedUsageLimitDetails, Announcement, Client, ClientInput, CreateStreamInput, DataRemovalRequestInput, Dataset, DatasetInvite, DatasetProjection, DataStream, Export, ExportInput, GlobalMetaStats, Grant, GraphQ, Invite, Issue, IssueActivity, IssueComment, IssueInput, IssueState, IssueUpdate, MetaStats, Metric, Origin, Page, PageArgs, Permission, ResourceType, Response, RestrictionInput, Role, ServiceStatus, Team, TeamInput, TeamInvite, TeamTiles, TeamUser, Thing, UpdateAnnouncement as InputAnnouncement, UpdateDatasetInput, UsageLimit, UsageLimitDetails, UsageLimitInput, UsagePlan, UsagePlanInput, User } from '@shared/model/types';
import { data } from '@shared/utils';
import { Observable } from 'rxjs';
import { map, switchMap, tap } from 'rxjs/operators';
import { ConfigService } from './config.service';
import { RoleService } from './role.service';

@Injectable({
  providedIn: 'root'
})
export class ObeliskService {
  private uri: string;


  constructor(private http: HttpClient, private role: RoleService, config: ConfigService, private modal: NgbModal) {
    this.uri = config.getCfg().oblxHost + config.getCfg().oblxApiPrefix;
  }

  private graphql(): string {
    return this.uri + '/catalog/graphql';
  }

  private path(path: string, pageArgs?: PageArgs) {
    const p = path.startsWith('/') ? this.uri + path : this.uri + '/' + path;
    let uri = p;
    if (pageArgs) {
      uri += '?' + Object.entries(pageArgs)
        .filter(([key, val]) => (val !== undefined && val !== null))
        .filter(([key, val]) => key != 'filter')
        .map(([key, val]) => `${key}=${val}`)
        .join('&');
    }
    if (pageArgs?.filter) {
      uri += '&' + Object.entries(pageArgs.filter)
        .filter(([key, val]) => (val !== undefined && val !== null))
        .map(([key, val]) => `${key}=${val}`)
        .join('&');
    }
    return uri;
  }

  /**
   * Post a metadata query
   * @param query Graphql query
   */
  private meta(query: GraphQ): Observable<Object[]> {
    return this.http.post<Object[]>(this.graphql(), query);
  };

  private handleErrors<T>(): (source: Observable<Response<T>>) => Observable<Response<T>> {
    const that = this;
    return function <T>(source: Observable<Response<T>>): Observable<Response<T>> {
      return source.pipe(tap(obj => {
        if (obj?.responseCode != 'SUCCESS') {
          const ref = that.modal.open(ErrorComponent, { backdrop: 'static' });
          ref.componentInstance.init(obj.responseCode, obj.message);
        }
      }));
    }
  }

  /**
   * Global metastast for the main page
   * @returns
   */
  getGlobalMetaStats(): Observable<GlobalMetaStats> {
    const query = `
      query {
        globalStats {
          nrOfDatasets
          nrOfMetrics
          nrOfUsers
          nrOfClients
          nrOfEvents
          totalSizeBytes
          ingestedEventsRate { timestamp value } 
          consumedEventsRate {  timestamp value } 
        }
      }
    `
    return this.meta({ query }).pipe(data('globalStats'));
  }

  listAnnouncements(pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<Announcement>> {
    const query = `
      query ($cursor: String, $limit: Int, $filter: AnnouncementFilter) {
        announcements(cursor: $cursor, limit: $limit, filter: $filter) {
          items {
            id
            timestamp
            title
            content
          }
          cursor
        }
      }
      `;
    const variables = { ...pageArgs };
    return this.meta({ query, variables }).pipe(data('announcements'));
  }

  getAnnouncement(id: string): Observable<Announcement> {
    const query = `
      query ($id: String!) {
        announcement(id: $id) {
            id
            timestamp
            title
            content
        }
      }
      `;
    const variables = { id };
    return this.meta({ query, variables }).pipe(data('announcement'));
  }

  createAnnouncement(input: InputAnnouncement): Observable<Response<Announcement>> {
    const query = `
    mutation ($input: CreateAnnouncement!) {
      createAnnouncement(input: $input) {
        responseCode
        message
      }
    }`
    const variables = { input };
    return this.meta({ query, variables }).pipe(data('createAnnouncement'), this.handleErrors());
  }

  updateAnnouncement(id: string, input: InputAnnouncement): Observable<Response<Announcement>> {
    const query = `
    mutation ($id: String!, $input: UpdateAnnouncement!) {
      onAnnouncement(id: $id) {
        update(input: $input) {
          responseCode
          message
        }
      }
    }`
    const variables = { id, input };
    return this.meta({ query, variables }).pipe(data('onAnnouncement', 'update'), this.handleErrors());
  }

  removeAnnouncement(id: string): Observable<Response<Announcement>> {
    const query = `
    mutation ($id: String!) {
      onAnnouncement(id: $id) {
        remove {
          responseCode
          message
        }
      }
    }`
    const variables = { id };
    return this.meta({ query, variables }).pipe(data('onAnnouncement', 'remove'), this.handleErrors());
  }

  setUserAsPlatformManger(id: string, platformManager: boolean): Observable<Response<User>> {
    const query = `
    mutation ($id: String!, $platformManager: Boolean!) {
      onUser(id: $id) {
        update(input: {platformManager: $platformManager}) {
          responseCode
          message
        }
      }
    }`
    const variables = { id, platformManager };
    return this.meta({ query, variables }).pipe(data('onUser', 'update'), this.handleErrors());
  }


  getStatusGlobal(maxRecords: number = 15): Observable<ServiceStatus> {
    return this.http.get<ServiceStatus>(this.path(`/monitor/status/global?maxRecords=${maxRecords}`));
  }

  getStatusAll(maxRecords: number = 15): Observable<ServiceStatus[]> {
    return this.http.get<ServiceStatus[]>(this.path(`/monitor/status?maxRecords=${maxRecords}`));
  }

  listMyDatasets(pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<Dataset>> {
    const query = `
      query ($cursor: String, $limit: Int, $filter: DatasetFilter) {
        me {
          datasets(cursor: $cursor, limit: $limit, filter: $filter) {
            items {
              id
              name
              description
              metaStats {
                lastUpdate
                nrOfMetrics
                nrOfEvents
              }
            }
            cursor
          }
        }
      }
      `;
    const variables = { ...pageArgs };
    return this.meta({ query, variables }).pipe(data('me', 'datasets'));
  }

  listTeamDatasets(teamId: number, pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<Dataset>> {
    const query = `
      query ($teamId: String!, $cursor: String, $limit: Int, $filter: DatasetFilter) {
        team(id: $teamId) {
          datasets(cursor: $cursor, limit: $limit, filter: $filter) {
            items {
              id
              name
              description
              metaStats {
                lastUpdate
                nrOfMetrics
                nrOfEvents
              }
            }
            cursor
          }
        }
      }
      `;
    const variables = { teamId, ...pageArgs };
    return this.meta({ query, variables }).pipe(data('team', 'datasets'));
  }

  listReadableDatasets(pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<Dataset>> {
    const query = `
      query ($cursor: String, $limit: Int, $filter: MembershipFilter) {
        me {
          memberships(cursor: $cursor, limit: $limit, filter: $filter) {
            items {
              dataset {
                id
                name
              }
              aggregatedGrant {
                permissions
              }
            }
            cursor
          }
        }
      }
      `;
    const variables = { ...pageArgs };
    return this.meta({ query, variables })
      .pipe(
        data<any>('me', 'memberships'),
        map<any, Page<Dataset>>(page => {
          page.items = page.items.map(entry => entry.dataset);
          return page;
        }));
  }

  listTeamReadableDatasets(teamId: string, pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<Dataset>> {
    const query = `
      query ($teamId: String!, $cursor: String, $limit: Int, $filter: MembershipFilter) {
        team (id: $teamId) {
          memberships(cursor: $cursor, limit: $limit, filter: $filter) {
            items {
              dataset {
                id
                name
              }
              aggregatedGrant {
                permissions
              }
            }
            cursor
          }
        }
      }
      `;
    const variables = { teamId, ...pageArgs };
    return this.meta({ query, variables })
      .pipe(
        data<any>('team', 'memberships'),
        map<any, Page<Dataset>>(page => {
          page.items = page.items.map(entry => entry.dataset);
          return page;
        }));
  }

  listMetrics(datasetId: string, pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<Metric>> {
    const query = `
      query ($datasetId: String!, $cursor: String, $limit: Int, $filter: MetricFilter) {
        dataset(id: $datasetId) {
          metrics(cursor: $cursor, limit: $limit, filter: $filter) {
            items {
                id
                started
                lastUpdate
            }
            cursor
          }
        }
      }
      `;
    const variables = { datasetId, ...pageArgs };
    return this.meta({ query, variables }).pipe(data('dataset', 'metrics'));
  }

  getMetric(datasetId: string, metricId: string): Observable<Metric> {
    const query = `
      query ($datasetId: String!, $metricId: String!) {
        dataset(id: $datasetId) {
          metric(id: $metricId) {
            id
            lastUpdate
            started
            properties
          }
        }
      }
      `;
    const variables = { datasetId, metricId };
    return this.meta({ query, variables }).pipe(data('dataset', 'metric'));
  }

  getOneMetric(datasetId: string): Observable<Metric> {
    const query = `
      query ($datasetId: String!) {
        dataset(id: $datasetId) {
          metrics(limit: 1) {
            items {
                id
                started
                lastUpdate
            }
            cursor
          }
        }
      }
      `;
    const variables = { datasetId };
    return this.meta({ query, variables }).pipe(data('dataset', 'metrics', 'items'), map((m: Metric[]) => m.length > 0 ? m[0] : null));
  }

  listThings(datasetId: string, pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<Metric>> {
    const query = `
      query ($datasetId: String!, $cursor: String, $limit: Int) {
        dataset(id: $datasetId) {
          things(cursor: $cursor, limit: $limit) {
            items {
              id
              started
              lastUpdate
            }
            cursor
          }
        }
      }
      `;
    const variables = { datasetId, ...pageArgs };
    return this.meta({ query, variables }).pipe(data('dataset', 'things'));
  }

  getThing(datasetId: string, thingId: string): Observable<Thing> {
    const query = `
      query ($datasetId: String!, $thingId: String!) {
        dataset(id: $datasetId) {
          thing(id: $thingId) {
            id
            started
            lastUpdate
          }
        }
      }
      `;
    const variables = { datasetId, thingId };
    return this.meta({ query, variables }).pipe(data('dataset', 'thing'));
  }

  listThingsOfMetric(datasetId: string, metricId: string, pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<Thing>> {
    const query = `
      query ($datasetId: String!, $metricId: String!, $cursor: String, $limit: Int, $filter: ThingFilter) {
        dataset(id: $datasetId) {
          metric(id: $metricId) {
            things(cursor: $cursor, limit: $limit, filter: $filter) {
              items {
                id
                started
                lastUpdate
              }
              cursor
            }
          }
        }
      }
      `;
    const variables = { datasetId, metricId, ...pageArgs };
    return this.meta({ query, variables }).pipe(data('dataset', 'metric', 'things'));
  }

  listMetricsOfThing(datasetId: string, thingId: string, pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<Metric>> {
    const query = `
      query ($datasetId: String!, $thingId: String!, $cursor: String, $limit: Int, $filter: MetricFilter) {
        dataset(id: $datasetId) {
          thing(id: $thingId) {
            metrics(cursor: $cursor, limit: $limit, filter: $filter) {
              items {
                id
                started
                lastUpdate
                properties
              }
              cursor
            }
          }
        }
      }
      `;
    const variables = { datasetId, thingId, ...pageArgs };
    return this.meta({ query, variables }).pipe(data('dataset', 'thing', 'metrics'));
  }

  createDataset(name: string, description?: string, ownerId?: string): Observable<Response<Dataset>> {
    const variables = {
      name,
      description,
      ownerId
    };
    const query = `mutation ($name: String!, $description: String, $ownerId: String) {
      createDataset(input: {name: $name, description: $description, datasetOwnerId: $ownerId}) {
        responseCode
        message
        item {
          id
        }
      }
    }`;
    return this.meta({
      query,
      variables
    }).pipe(data('createDataset'), this.handleErrors());
  }

  /**
   * List all published datasets.
   */
  listPublishedDatasets(pageArgs: PageArgs = { cursor: null, limit: 25, filter: {} }): Observable<Page<Dataset>> {
    const query = `
      query ($cursor: String, $limit: Int, $filter: DatasetFilter) {
        publishedDatasets(cursor: $cursor, limit: $limit, filter: $filter) {
          items {
            id
            name
            description
            metaStats {
              lastUpdate
              nrOfMetrics
              nrOfEvents
            }
          }
          cursor
        }
      }`
    const variables = { ...pageArgs };
    return this.meta({ query, variables }).pipe(data('publishedDatasets'));
  }

  getPublicDataset(id: string): Observable<Dataset> {
    return this.meta({
      query: `query getDataset($id: String!) {
          dataset(id: $id) {
            id
            name
            description
          }
      }`,
      variables: { id }
    }).pipe(data('dataset'));
  }

  /**
   * Fetch all fields important for the Header subpage
   */
  getDatasetHeader(id: string): Observable<Dataset> {
    return this.meta({
      query: `query getDataset($id: String!) {
        dataset(id: $id) {
          id
          name
        }
      }`,
      variables: { id }
    }).pipe(data('dataset'));
  }

  /**
   * Fetch all fields imporant for the Edit subpage
   * @param id 
   */
  getDatasetSettings(id: string): Observable<Dataset> {
    return this.meta({
      query: `query getDataset($id: String!) {
        dataset(id: $id) {
          id
          name
          description
          published
          openData
          keywords
          license
          contactPoint
          publisher {
            name
            homepage
          }
        }
      }`,
      variables: { id }
    }).pipe(data('dataset'));
  }

  getDatasetOverview(id: string): Observable<Dataset> {
    return this.meta({
      query: `query getDataset($id: String!) {
        dataset(id: $id) {
          id
          name
          description
          published
          openData
          keywords
          license
          contactPoint
          publisher {
            name
            homepage
          }
        }
      }`,
      variables: { id }
    }).pipe(data('dataset'));
  }

  getDatasetMetaStats(id: string): Observable<MetaStats> {
    return this.meta({
      query: `query getDataset($id: String!) {
        dataset(id: $id) {
          metaStats {
            lastUpdate
            nrOfMetrics
            nrOfEvents
            approxSizeBytes
            nrOfStreams
            ingestApiRequestRate { timestamp value }
            ingestedEventsRate { timestamp value }
            eventsQueryApiRequestRate { timestamp value }
            statsQueryApiRequestRate { timestamp value }
            consumedEventsRate { timestamp value }
            queriesConsumedEventsRate { timestamp value }
            streamingConsumedEventsRate { timestamp value }
            activeStreams { timestamp value }
          }
        }
      }`,
      variables: { id }
    }).pipe(data('dataset', 'metaStats'));
  }

  getDatasetProjection(id: string): Observable<DatasetProjection> {
    return this.meta({
      query: `query getDataset($id: String!) {
        dataset(id: $id) {
          metaStats {
            nrOfEvents
            nrOfEventsProjection
            approxSizeBytes
            approxSizeBytesProjection
          }
        }
      }`,
      variables: { id }
    }).pipe(data('dataset', 'metaStats'));
  }

  getDatasetPeek(id: string): Observable<Dataset> {
    return this.meta({
      query: `query getDataset($id: String!) {
        dataset(id: $id) {
          id
          name
          description
          published
          openData
          keywords
          license
          contactPoint
          publisher {
            name
            homepage
          }
          metaStats {
            lastUpdate
            nrOfMetrics
            nrOfEvents
            approxSizeBytes
            ingestRate { label values { timestamp value } }
            eventsConsumptionRate { label values { timestamp value } }
            eventsRequestRate { label values { timestamp value } }
            statsRequestRate { label values { timestamp value } }
          }
        }
      }`,
      variables: { id }
    }).pipe(data('dataset'));
  }

  getDataset(id: string): Observable<Dataset> {
    return this.meta({
      query: `query getDataset($id: String!) {
        dataset(id: $id) {
          origins
          description
          published
          openData
          keywords
          license
          contactPoint
          publisher {
            name
            homepage
          }
          metaStats {
            lastUpdate
            nrOfMetrics
            nrOfEvents
            approxSizeBytes
            ingestRate { label values { timestamp value } }
            eventsConsumptionRate { label values { timestamp value } }
            eventsRequestRate { label values { timestamp value } }
            statsRequestRate { label values { timestamp value } }
          }
        }
      }`,
      variables: { id }
    }).pipe(data('dataset'));
  }

  getDatasetAsAdmin(id: string): Observable<Dataset> {
    return this.meta({
      query: `query getDataset($id: String!) {
        admin { 
          dataset(id: $id) {
            id
            name
            description
            published
            archived
            openData
            keywords
            license
            contactPoint
            publisher {
              name
              homepage
            }
            metaStats {
              lastUpdate
              nrOfMetrics
              nrOfEvents
              approxSizeBytes
              ingestRate { label values { timestamp value } }
              eventsConsumptionRate { label values { timestamp value } }
              eventsRequestRate { label values { timestamp value } }
              statsRequestRate { label values { timestamp value } }
            }
            metrics(limit: 1) {
              items {
                id
              }
            }
          }
        }
      }`,
      variables: { id }
    }).pipe(data('admin', 'dataset'));
  }

  getDatasetMember(datasetId: string, userId: string): Observable<User> {
    return this.meta({
      query: `
      query ($datasetId: String!, $userId: String!) {
        dataset(id: $datasetId) {
          member(id: $userId) {
            id
            email
            firstName
            lastName
            membership(id: $datasetId) {
              roles {
                id
                name
              }
            }
          }
        }
      }`,
      variables: { datasetId, userId }
    }).pipe(data('dataset', 'member'));
  }

  getDatasetMembers(id: string, pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<User>> {
    return this.meta({
      query: `
      query ($id: String!, $cursor: String, $limit: Int) {
        dataset(id: $id) {
          members(cursor: $cursor, limit: $limit) {
            items {
              id
              email
              firstName
              lastName
            }
            cursor
          }
        }
      }`,
      variables: { ...pageArgs, id }
    }).pipe(data('dataset', 'members'));
  }

  getDatasetMemberRoles(id: string, pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<User>> {
    return this.meta({
      query: `
      query ($id: String!, $cursor: String, $limit: Int, $filter: UserFilter) {
        dataset(id: $id) {
          members(cursor: $cursor, limit: $limit, filter: $filter) {
            items {
              id
              email
              firstName
              lastName
              membership(id: $id) {
                roles {
                  id
                  name
                }
              }
            }
            cursor
          }
        }
      }`,
      variables: { ...pageArgs, id }
    }).pipe(data('dataset', 'members'));
  }

  getDatasetTeams(id: string, pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<Team>> {
    return this.meta({
      query: `
      query ($id: String!, $cursor: String, $limit: Int, $filter: TeamFilter) {
        dataset(id: $id) {
          teams(cursor: $cursor, limit: $limit, filter: $filter) {
            items {
              id
              name
              description
            }
            cursor
          }
        }
      }`,
      variables: { ...pageArgs, id }
    }).pipe(data('dataset', 'teams'));
  }

  /**
   * Get the team that is a member of the dataset
   * @param datasetId 
   * @param teamId
   * @returns 
   */
  getDatasetTeam(datasetId: string, teamId: string): Observable<Team> {
    return this.meta({
      query: `
      query ($datasetId: String!, $teamId: String!) {
        dataset(id: $datasetId) {
          team(id: $teamId) {
              id
              name
              description
              membership(id: $datasetId) {
                roles {
                  id
                  name
                }
              }
          }
        }
      }`,
      variables: { datasetId, teamId }
    }).pipe(data('dataset', 'team'));
  }

  countDatasetAccessReqeuests(id: string): Observable<number> {
    return this.meta({
      query: `
      query ($id: String!) {
        dataset(id: $id) {
          accessRequests {
            count
          }
        }
      }`,
      variables: { id }
    }).pipe(data('dataset', 'accessRequests', 'count'));
  }


  listDatasetAccessReqeuests(id: string, pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<AccessRequest>> {
    return this.meta({
      query: `
      query ($id: String!, $cursor: String, $limit: Int) {
        dataset(id: $id) {
          accessRequests (cursor: $cursor, limit: $limit) {
            items {
              id
              timestamp
              user {
                id email firstName lastName
              }
              team {
                id name description
              }
              dataset {
                id
                name
              }
              type 
              message
              status
            }
            cursor
          }
        }
      }`,
      variables: { id, ...pageArgs }
    }).pipe(data('dataset', 'accessRequests'));
  }

  /**
   * List all access requests created by me. Clientside filtering on datasetId is possible.
   * **Careful! With syntax listMyAccessRequests.bind(this.obelisk, null). Null is required or the implied pageArgs argument gets inserted there.**
   * 
   * @param datasetId Null or a string. If null, does not filtering
   * @param pageArgs 
   * @returns 
   */
  listMyAccessRequests(datasetId: string | null, pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<AccessRequest>> {
    const variables = {
      ...pageArgs
    }
    const query = `
      query ($cursor: String, $limit: Int)  {
        me {
          accessRequests(cursor: $cursor, limit: $limit) {
            items {
              id
              timestamp
              type
              message
              user {
                id
              }
              team {
                id
                name
              }
              status
              dataset {
                id name
              }
            }
            cursor
          }
        }
      }
      `;
    return this.meta({ query, variables }).pipe(
      data<Page<AccessRequest>>('me', 'accessRequests'),
      map(page => {
        if (datasetId) {
          page.items = page.items.filter(item => item.dataset.id === datasetId);
        }
        return page;
      }));
  }

  acceptAccessRequest(datasetId: string, accessRequestId: string, roleIds?: string[]): Observable<Response<AccessRequest>> {
    return this.meta({
      query: `mutation approveAccessRequest($datasetId:String!, $accessRequestId: String!, $roleIds: [String!]) {
        onDataset(id: $datasetId) {
          onAccessRequest(id: $accessRequestId) {
            accept(roleIds: $roleIds) {
              responseCode
              message
            }
          }
        }
      }`,
      variables: { datasetId, accessRequestId, roleIds }
    }).pipe(data('onDataset', 'onAccessRequest', 'accept'), this.handleErrors());
  }

  listDatasetOrigins(id: string, pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<Origin>> {
    return this.meta({
      query: `
      query ($id: String!, $cursor: String, $limit: Int) {
        dataset(id: $id) {
          origins(cursor: $cursor, limit: $limit) {
            items {
              producer {
                __typename
                ... on Client {
                  id
                  name
                }
                ... on User {
                  id
                  email
                  firstName
                  lastName
                }
              }
              started
              lastUpdate
            }
            cursor
          }
        }
      }`,
      variables: { ...pageArgs, id }
    }).pipe(data('dataset', 'origins'));
  }

  updateDataset(id: string, input: UpdateDatasetInput): Observable<Response<Dataset>> {
    const query = `
      mutation ($id: String!, $input: UpdateDatasetInput!) {
        onDataset(id: $id) {
          update(input: $input) {
            responseCode
            message
          }
        }
      }
    `;
    const variables = { id, input };
    return this.meta({ query, variables }).pipe(data('onDataset', 'update'), this.handleErrors());
  }

  editDatasetDescription(id: string, description: string): Observable<Response<Dataset>> {
    return this.meta({
      query: `mutation editDatasetDescription($id:String!, $description: String!) {
        onDataset(id: $id) {
          setDescription(description: $description) {
            responseCode
            message
          }
        }
      }`,
      variables: { id, description }
    }).pipe(data('onDataset', 'setDescription'), this.handleErrors());
  }

  addDatasetMember(datasetId: string, userId: string): Observable<Response<Dataset>> {
    return this.meta({
      query: `mutation ($datasetId:String!, $userId: String!) {
        onDataset(id: $datasetId) {
          addMember(userId: $userId) {
            responseCode
            message
          }
        }
      }`,
      variables: { datasetId, userId }
    }).pipe(data('onDataset', 'addMember'), this.handleErrors());
  }

  addDatasetTeam(datasetId: string, teamId: string): Observable<Response<Dataset>> {
    return this.meta({
      query: `mutation ($datasetId:String!, $teamId: String!) {
        onDataset(id: $datasetId) {
          addTeam(teamId: $teamId) {
            responseCode
            message
          }
        }
      }`,
      variables: { datasetId, teamId }
    }).pipe(data('onDataset', 'addTeam'), this.handleErrors());
  }

  removeDatasetMember(datasetId: string, userId: string): Observable<Response<Dataset>> {
    return this.meta({
      query: `mutation ($datasetId:String!, $userId: String!) {
        onDataset(id: $datasetId) {
          removeMember(userId: $userId) {
            responseCode
            message
          }
        }
      }`,
      variables: { datasetId, userId }
    }).pipe(data('onDataset', 'removeMember'), this.handleErrors());
  }

  removeDatasetTeam(datasetId: string, teamId: string): Observable<Response<Dataset>> {
    return this.meta({
      query: `mutation ($datasetId:String!, $teamId: String!) {
        onDataset(id: $datasetId) {
          removeTeam(teamId: $teamId) {
            responseCode
            message
          }
        }
      }`,
      variables: { datasetId, teamId }
    }).pipe(data('onDataset', 'removeTeam'), this.handleErrors());
  }

  uploadResource(formData: FormData, datasetId: string, resourceType: 'thumbnail' | 'banner' | 'readme' | 'other', overwrite: boolean) {
    return this.http.post(this.path(`/catalog/resources/${datasetId}?resourceType=${resourceType}&overwrite=${overwrite}`), formData);
  }

  deleteReadMe(datasetId: string) {
    return this.http.delete(this.path(`/catalog/resources/${datasetId}/readme.md`));
  }

  getResourceUrl(datasetId: string, resourceType: ResourceType) {
    let file = resourceType;
    switch (resourceType) {
      case 'banner':
      case 'thumbnail':
        file += '.png';
        break;
      case 'readme':
        file += '.md';
        break;
    }
    return this.path(`/catalog/resources/${datasetId}/${file}`);
  }

  getEvents(datasetId: string, metricId: string) {
    const body = {
      dataRange: {
        datasets: [datasetId],
        metrics: [metricId]
      },
    };
    return this.http.post(
      this.path('/data/query/events'), body);
  }

  getAggregates(datasetId: string, metricId: string): Observable<any> {
    const body = {
      dataRange: {
        datasets: [datasetId],
        metrics: [metricId]
      },
      fields: ['source', 'mean'],
      groupBy: {
        time: {
          interval: 1,
          intervalUnit: 'minutes',
        },
        fields: ['source']
      }
    };
    return this.http.post(this.path('/data/query/stats'), body);
  }

  getProfile(): Observable<User> {
    const query = `{
      me {
        id
        email
        firstName
        lastName
        platformManager
      }
    }`
    return this.meta({ query }).pipe(data('me'));
  }

  getClient(clientId: string): Observable<Client> {
    const query = `
      query ($clientId: String!) {
        me {
          client(id: $clientId) {
            id
              user {
                id
                firstName
                lastName
                email
              }
              team {
                id
                name
              }
              name
              confidential
              onBehalfOfUser
              properties
              scope
              redirectURIs
              restrictions {
                dataset {
                  id
                  name
                }
                permissions
              }
          }
        }
      }`;
    const variables = { clientId };
    return this.meta({ query, variables }).pipe(data('me', 'client'));
  }

  getClientAsAdmin(clientId: string): Observable<Client> {
    const query = `
      query ($clientId: String!) {
        admin {
          client(id: $clientId) {
            id
              user {
                id
                firstName
                lastName
                email
              }
              team {
                id
                name
              }
              name
              confidential
              onBehalfOfUser
              properties
              scope
              redirectURIs
              restrictions {
                dataset {
                  id
                  name
                }
                permissions
              }
          }
        }
      }`;
    const variables = { clientId };
    return this.meta({ query, variables }).pipe(data('admin', 'client'));
  }

  getTeamClient(teamId: string, clientId: string): Observable<Client> {
    const query = `
      query ($teamId: String!, $clientId: String!) {
        team(id: $teamId) {
          client(id: $clientId) {
            id
            user {
              id
              firstName
              lastName
              email
            }
            team {
              id
              name
            }
            name
            confidential
            onBehalfOfUser
            properties
            scope
            redirectURIs
            restrictions {
              dataset {
                id
                name
              }
              permissions
            }
          }
        }
      }`;
    const variables = { teamId, clientId };
    return this.meta({ query, variables }).pipe(data('team', 'client'));
  }

  listMyClients(pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<Client>> {
    const query = `query ($cursor: String, $limit: Int, $filter: ClientFilter) {
      me {
        clients (cursor: $cursor, limit: $limit, filter: $filter) {
          items {
            id
            user {
              id
            }
            team {
              id
              name
            }
            name
            confidential
            onBehalfOfUser
            properties
            scope
            redirectURIs
            restrictions {
              dataset {
                id
                name
              }
              permissions
            }
          }
          cursor
        }
      }
    }`
    const variables = { ...pageArgs };
    return this.meta({ query, variables }).pipe(data('me', 'clients'));
  }

  /**
   * Lists all team clients
   */
  listTeamClients(teamId: string, pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<Client>> {
    const query = `query ($teamId: String!, $cursor: String, $limit: Int, $filter: ClientFilter) {
      team(id: $teamId) {
        clients (cursor: $cursor, limit: $limit, filter: $filter) {
          items {
            id
            user {
              id
              email
              firstName
              lastName
            }
            name
            confidential
            onBehalfOfUser
            hideOrigin
            properties
            scope
            redirectURIs
            restrictions {
              dataset {
                id
                name
              }
              permissions
            }
            usageLimit {
              id
              name
              description
              defaultLimit
              values
            }
            usageRemaining
          }
          cursor
        }
      }
    }`
    const variables = { teamId, ...pageArgs };
    return this.meta({ query, variables }).pipe(data('team', 'clients'));
  }

  listMyTeamClients(teamId: string, pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<Client>> {
    const query = `query ($teamId: String!, $cursor: String, $limit: Int, $filter: ClientFilter) {
      me {
        team(id: $teamId) {
          clients (cursor: $cursor, limit: $limit, filter: $filter){
            items {
              id
              user {
                id
              }
              name
              confidential
              onBehalfOfUser
              properties
              scope
              redirectURIs
              hideOrigin
              restrictions {
                dataset {
                  id
                  name
                }
                permissions
              }
              usageLimit {
                id
                name
                description
                defaultLimit
                values
              }
              usageRemaining
            }
            cursor
          }
        }
      }
    }`
    const variables = { teamId, ...pageArgs }
    return this.meta({ query, variables }).pipe(data('me', 'team', 'clients'));
  }

  /**
   * Create a (user) Client
   */
  createClient(input: ClientInput): Observable<Response<Client>> {
    let query = `
      mutation ($input: CreateClientInput!) {
        createClient(input: $input) {
          item {
            id
            name
          }
          message,
          responseCode
        }
      }
    `;
    const variables = { input };
    const name = input.name;
    const confidentialAddition = switchMap<any, Observable<Response<Client>>>(res => this.generateSecretForClient(res.item.id)
      .pipe(map(secret => ({ ...res, ...{ item: { name, secret, id: res.item.id } } }))));
    const obs = this.meta({ query, variables }).pipe(data('createClient'));
    if (input.confidential) {
      return obs.pipe(confidentialAddition, this.handleErrors());
    } else {
      return (obs as Observable<Response<Client>>).pipe(this.handleErrors());
    }
  }

  /**
   * Create Team Client
   */
  createTeamClient(teamId: string, input: ClientInput): Observable<Response<Client>> {
    let query = `
      mutation ($teamId: String!, $input: CreateClientInput!) {
        onTeam(id: $teamId) {
          createClient(input: $input) {
            item {
              id
              name
            }
            message
            responseCode
          }
        }
      }
    `;
    const variables = { teamId, input };
    const name = input.name;
    const confidentialAddition = switchMap<any, Observable<Response<Client>>>(res => this.generateSecretForClient(res.item.id)
      .pipe(map(secret => ({ ...res, ...{ item: { name, secret, id: res.item.id } } }))));
    const obs = this.meta({ query, variables }).pipe(data('onTeam', 'createClient'));
    if (input.confidential) {
      return obs.pipe(confidentialAddition, this.handleErrors());
    } else {
      return (obs as Observable<Response<Client>>).pipe(this.handleErrors());
    }
  }


  generateSecretForClient(clientId: string): Observable<string> {
    let query = `
    mutation {
      onClient(id: "${clientId}") {
        generateSecret {
          item
      }
    }
  }
  `;
    return this.meta({ query }).pipe(data('onClient', 'generateSecret', 'item'));
  }

  removeClient(clientId: string): Observable<Response<Client>> {
    const query = `
    mutation ($clientId: String!) {
      onClient(id: $clientId) {
        remove {
          responseCode
          message
        }
      }
    }`;
    const variables = { clientId };
    return this.meta({ query, variables }).pipe(data('onClient', 'remove'), this.handleErrors());
  }

  archiveDataset(datasetId: string): Observable<Response<Dataset>> {
    const query = `
    mutation ($datasetId: String!) {
      onDataset(id: $datasetId) {
        archive {
          responseCode
          message
        }
      }
    }`;
    const variables = { datasetId };
    return this.meta({ query, variables }).pipe(data('onDataset', 'archive'), this.handleErrors());
  }

  unarchiveDataset(datasetId: string): Observable<Response<Dataset>> {
    const query = `
    mutation ($datasetId: String!) {
      onDataset(id: $datasetId) {
        unarchive {
          responseCode
          message
        }
      }
    }`;
    const variables = { datasetId };
    return this.meta({ query, variables }).pipe(data('onDataset', 'unarchive'), this.handleErrors());
  }

  removeDataset(datasetId: string): Observable<Response<Dataset>> {
    const query = `
    mutation ($datasetId: String!) {
      onDataset(id: $datasetId) {
        remove {
          responseCode
          message
        }
      }
    }`;
    const variables = { datasetId };
    return this.meta({ query, variables }).pipe(data('onDataset', 'remove'), this.handleErrors());
  }

  setClientScope(clientId: string, scope: Permission[]): Observable<Response<Client>> {
    const query = `
      mutation ($clientId: String!, $scope: [Permission]!) {
        onClient(id: $clientId) {
          setScope(permissions: $scope) {
            responseCode
            message
            item {
              scope
            }
          }
        }
      }`;
    const variables = { clientId, scope };
    return this.meta({ query, variables }).pipe(data('onClient', 'setScope'), this.handleErrors());
  }

  setClientRedirectURIs(clientId: string, URIs: string[]): Observable<Response<Client>> {
    const query = `
      mutation ($clientId: String!, $URIs: [String]!) {
        onClient(id: $clientId) {
          setRedirectURIs(redirectURIs: $URIs) {
            responseCode
            message
            item {
              redirectURIs
            }
          }
        }
      }`;
    const variables = { clientId, URIs };
    return this.meta({ query, variables }).pipe(data('onClient', 'setRedirectURIs'), this.handleErrors());
  }

  setClientRestrictions(clientId: string, restrictions: RestrictionInput[]): Observable<Response<Client>> {
    const query = `
      mutation ($clientId: String!, $restrictions: [ClientRestrictionInput]!) {
        onClient(id: $clientId) {
          setRestrictions(restrictions: $restrictions) {
            responseCode
            message
          }
        }
      }`;
    const variables = { clientId, restrictions };
    return this.meta({ query, variables }).pipe(data('onClient', 'setRestrictions'), this.handleErrors());
  }

  grantUserRights(datasetId: string, userId: string, permissions: Permission[], readFilter?: any): Observable<Response<Dataset>> {
    const query = `
      mutation ($datasetId: String!, $userId: String!, $permissions: [Permission!]!, $readFilter: JSON) {
        onDataset(id: $datasetId) {
          grantAccessToUser(input: {subjectId: $userId, permissions: $permissions, readFilter: $readFilter}) {
            responseCode
            message
          }
        }
      }`;
    const variables = { datasetId, userId, permissions, readFilter };
    return this.meta({ query, variables }).pipe(data('onDataset', 'grantAccessToUser'), this.handleErrors());
  }

  grantGroupRights(datasetId: string, userId: string, permissions: Permission[], readFilter?: any): Observable<Response<Dataset>> {
    const query = `
      mutation ($datasetId: String!, $userId: String!, $permissions: [Permission!]!, $readFilter: JSON) {
        onDataset(id: $datasetId) {
          grantAccessToGroup(input: {subjectId: $userId, permissions: $permissions, readFilter: $readFilter}) {
            responseCode
            message
          }
        }
      }`;
    const variables = { datasetId, userId, permissions, readFilter };
    return this.meta({ query, variables }).pipe(data('onDataset', 'grantAccessToUser'), this.handleErrors());
  }

  /**
   * Returns a dateset merged with the invite.
   */
  getDatasetInvite(datasetId: string, inviteId: string,): Observable<Dataset> {
    const query = `
      query ($datasetId: String!, $inviteId: String!) {
        dataset(id: $datasetId) {
          id
          name
          description
          invite(id: $inviteId) {
            id
            roles {
              name
              description
            }
            expiresInMs
            disallowTeams
          }
        }
      }
    `;
    const variables = {
      datasetId, inviteId
    }
    return this.meta({ query, variables }).pipe(data('dataset'));
  }

  acceptDatasetInvite(datasetId: string, inviteId: string): Observable<Response<Invite>> {
    const query = `
      mutation ($datasetId: String!, $inviteId: String!) {
        onDataset(id: $datasetId) {
          onInvite(id: $inviteId) {
            accept {
              responseCode
              message
            }
          }
        }
      }
    `;
    const variables = {
      datasetId, inviteId
    }
    return this.meta({ query, variables }).pipe(data('onDataset', 'onInvite', 'accept'), this.handleErrors());
  }

  acceptDatasetInviteAsTeam(datasetId: string, inviteId: string, teamId: string): Observable<Response<Invite>> {
    const query = `
      mutation ($datasetId: String!, $inviteId: String!, $teamId: String!) {
        onDataset(id: $datasetId) {
          onInvite(id: $inviteId) {
            acceptAsTeam(teamId: $teamId) {
              responseCode
              message
            }
          }
        }
      }
    `;
    const variables = {
      datasetId, inviteId, teamId
    }
    return this.meta({ query, variables }).pipe(data('onDataset', 'onInvite', 'acceptAsTeam'), this.handleErrors());
  }

  revokeDatasetInvite(datasetId: string, inviteId: string): Observable<Response<Invite>> {
    const query = `
      mutation ($datasetId: String!, $inviteId: String!) {
        onDataset(id: $datasetId) {
          onInvite(id: $inviteId) {
            revoke {
              responseCode
              message
            }
          }
        }
      }
    `;
    const variables = {
      datasetId, inviteId
    }
    return this.meta({ query, variables }).pipe(data('onDataset', 'onInvite', 'revoke'), this.handleErrors());
  }

  getDatasetRights(datasetId: string): Observable<Grant[]> {
    const query = `
      query ($datasetId: String!) {
        dataset(id: $datasetId) {
          grants {
            subject {
              id
            }
            permissions
            readFilter
          }
        }
      }
    `
    const variables = { datasetId };
    return this.meta({ query, variables }).pipe(data('dataset', 'grants'));
  }

  getMyAggregatedDatasetRights(datasetId: string): Observable<Grant> {
    const query = `
    query ($datasetId: String!) {
      me {
        membership(id: $datasetId) {
          aggregatedGrant {
            permissions
            readFilter
          }
        }
      }
    }
  `
    const variables = { datasetId };
    return this.meta({ query, variables }).pipe(data('me', 'membership', 'aggregatedGrant'));
  }

  listMyStreams(pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<DataStream>> {
    const query = `
      query ($cursor: String, $limit: Int, $filter: DataStreamFilter) {
        me {
          activeStreams(cursor: $cursor, limit: $limit, filter: $filter) {
            items {
              id
              name
              dataRange {
                datasets { id name }
                metrics
              }
              timestampPrecision
              fields
              filter
              clientConnected
            }
            cursor
          }
        }
      }
    `
    const variables = { ...pageArgs }
    return this.meta({ query, variables }).pipe(data('me', 'activeStreams'));
  }

  listTeamStreams(teamId: string, pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<DataStream>> {
    const query = `
      query ($teamId: String!, $cursor: String, $limit: Int, $filter: DataStreamFilter) {
        team(id: $teamId) {
          activeStreams(cursor: $cursor, limit: $limit, filter: $filter) {
            items {
              id
              name
              dataRange {
                datasets { id name }
                metrics
              }
              timestampPrecision
              fields
              filter
              clientConnected
            }
            cursor
          }
        }
      }
    `
    const variables = { teamId, ...pageArgs }
    return this.meta({ query, variables }).pipe(data('team', 'activeStreams'));
  }

  createStream(input: CreateStreamInput): Observable<Response<DataStream>> {
    const variables = { input };
    const query = `mutation ($input: CreateStreamInput!) {
      createStream(input: $input) {
        responseCode
        message
      }
    }`;
    return this.meta({
      query,
      variables
    }).pipe(data('createStream'), this.handleErrors());
  }

  createTeamStream(teamId: string, input: CreateStreamInput): Observable<Response<DataStream>> {
    const variables = { teamId, input };
    const query = `mutation ($teamId: String!, $input: CreateStreamInput!) {
      onTeam(id: $teamId) {
        createStream(input: $input) {
          responseCode
          message
        }
      }
    }`;
    return this.meta({
      query,
      variables
    }).pipe(data('onTeam', 'createStream'), this.handleErrors());
  }

  removeStream(id: string): Observable<Response<DataStream>> {
    const variables = { id };
    const query = `mutation ($id: String!) {
      onStream(id: $id) {
        remove {
          responseCode
          message
        }
      }
    }`;
    return this.meta({
      query,
      variables
    }).pipe(data('onStream', 'remove'), this.handleErrors());
  }

  endStreamSession(id: string): Observable<Response<DataStream>> {
    const variables = { id };
    const query = `mutation ($id: String!) {
      onStream(id: $id) {
        endSession {
          responseCode
          message
        }
      }
    }`;
    return this.meta({
      query,
      variables
    }).pipe(data('onStream', 'endSession'), this.handleErrors());
  }

  removeTeamStream(teamId: string, id: string): Observable<Response<DataStream>> {
    const variables = { teamId, id };
    const query = `mutation ($teamId: String!, $id: String!) {
      onTeam(id: $teamId) {
        onStream(id: $id) {
          remove {
            responseCode
            message
          }
        }
      }
    }`;
    return this.meta({
      query,
      variables
    }).pipe(data('onTeam', 'onStream', 'remove'), this.handleErrors());

  }

  endTeamStreamSession(teamId: string, id: string): Observable<Response<DataStream>> {
    const variables = { teamId, id };
    const query = `mutation ($teamId: String!, $id: String!) {
      onTeam(id: $teamId) {
        onStream(id: $id) {
          endSession {
            responseCode
            message
          }
        }
      }
    }`;
    return this.meta({
      query,
      variables
    }).pipe(data('onTeam', 'onStream', 'endSession'), this.handleErrors());
  }

  listMyExports(pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<Export>> {
    const query = `
      query ($cursor: String, $limit: Int, $filter: DataExportFilter) {
        me {
          exports(cursor: $cursor, limit: $limit, filter: $filter) {
            items {
              id
              name
              dataRange {
                datasets { id name }
                metrics
              }
              timestampPrecision
              fields
              filter
              from
              to
              limit
              requestedOn
              status {
                status
                recordsEstimate
                recordsProcessed
              }
              result {
                completedOn
                expiresOn
                sizeInBytes
                compressedSizeInBytes
              }
            }
            cursor
          }
        }
      }
    `
    const variables = { ...pageArgs }
    return this.meta({ query, variables }).pipe(data('me', 'exports'));
  }

  getExport(id: string): Observable<Export> {
    const query = `
      query ($id: String!) {
        me {
          export(id: $id) {
            id
            name
            dataRange {
              datasets { id name }
              metrics
            }
            timestampPrecision
            fields
            filter
            from
            to
            limit
            requestedOn
            status {
              status
              recordsEstimate
              recordsProcessed
            }
            result {
              completedOn
              expiresOn
              sizeInBytes
              compressedSizeInBytes
            }
          }
        }
      }
    `
    const variables = { id }
    return this.meta({ query, variables }).pipe(data('me', 'export'));
  }

  createExport(input: ExportInput): Observable<Response<Export>> {
    const variables = { input };
    const query = `mutation ($input: CreateExportInput!) {
      createExport(input: $input) {
        responseCode
        message
      }
    }`;
    return this.meta({
      query,
      variables
    }).pipe(data('createExport'), this.handleErrors());
  }

  removeExport(id: string): Observable<Response<Export>> {
    const variables = { id };
    const query = `mutation ($id: String!) {
      onExport(id: $id) {
        remove {
          responseCode
          message
        }
      }
    }`;
    return this.meta({
      query,
      variables
    }).pipe(data('onExport', 'remove'), this.handleErrors());
  }

  listTeamExports(teamId: string, pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<Export>> {
    const query = `
      query ($teamId: String!, $cursor: String, $limit: Int, $filter: DataExportFilter) {
        team(id: $teamId) {
          exports(cursor: $cursor, limit: $limit, filter: $filter) {
            items {
              id
              name
              dataRange {
                datasets { id name }
                metrics
              }
              timestampPrecision
              fields
              filter
              from
              to
              limit
              requestedOn
              status {
                status
                recordsEstimate
                recordsProcessed
              }
              result {
                completedOn
                expiresOn
                sizeInBytes
                compressedSizeInBytes
              }
            }
            cursor
          }
        }
      }
    `
    const variables = { teamId, ...pageArgs }
    return this.meta({ query, variables }).pipe(data('team', 'exports'));
  }

  getTeamExport(teamId: string, id: string): Observable<Export> {
    const query = `
      query ($teamId: String!, $id: String!) {
        team(id: $teamId) {
          export(id: $id) {
            id
            name
            dataRange {
              datasets { id name }
              metrics
            }
            timestampPrecision
            fields
            filter
            from
            to
            limit
            requestedOn
            status {
              status
              recordsEstimate
              recordsProcessed
            }
            result {
              completedOn
              expiresOn
              sizeInBytes
              compressedSizeInBytes
            }
          }
        }
      }
    `
    const variables = { teamId, id }
    return this.meta({ query, variables }).pipe(data('team', 'export'));
  }

  createTeamExport(teamId: string, input: ExportInput): Observable<Response<Export>> {
    const variables = { teamId, input };
    const query = `mutation ($teamId: String!, $input: CreateExportInput!) {
      onTeam(id: $teamId) {
        createExport(input: $input) {
          responseCode
          message
        }
      }
    }`;
    return this.meta({
      query,
      variables
    }).pipe(data('onTeam', 'createExport'), this.handleErrors());
  }

  removeTeamExport(teamId: string, id: string): Observable<Response<Export>> {
    const variables = { teamId, id };
    const query = `mutation ($teamId: String!, $id: String!) {
      onTeam(id: $teamId) {
        onExport(id: $id) {
          remove {
            responseCode
            message
          }
        }
      }
    }`;
    return this.meta({
      query,
      variables
    }).pipe(data('onTeam', 'onExport', 'remove'), this.handleErrors());
  }


  /******************************************************************************************** */

  /**
     * List ALL datasets (also UNpublished) - needs PLATFORM_OWNER clearance
     */
  listAllDatasets(pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<Dataset>> {
    const query = `
      query ($cursor: String, $limit: Int, $filter: DatasetFilter) {
        admin {
          datasets(cursor: $cursor, limit: $limit, filter: $filter) {
            items {
              id
              name
              archived
              published
              openData
              properties
            }
            cursor
          }
        }
      }
      `;
    const variables = { ...pageArgs };
    return this.meta({ query, variables }).pipe(data('admin', 'datasets'));
  }

  listAllClients(pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<Client>> {
    const query = `query ($cursor: String, $limit: Int, $filter: ClientFilter) {
      admin {
        clients (cursor: $cursor, limit: $limit, filter: $filter) {
          items {
            id
            user {
              id
              email
              lastName
              firstName
            }
            team {
              id
              name
            }
            name
            confidential
            onBehalfOfUser
            properties
            scope
            redirectURIs
            restrictions {
              dataset {
                id
                name
              }
              permissions
            }
          }
          cursor
        }
      }
    }`
    const variables = { ...pageArgs };
    return this.meta({ query, variables }).pipe(data('admin', 'clients'));
  }


  listAllUsers(pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<User>> {
    const query = `
      query ($cursor: String, $limit: Int, $filter: UserFilter) {
        admin {
          users(cursor: $cursor, limit: $limit, filter: $filter) {
            items { 
              id
              email
              firstName
              lastName
              platformManager
            }
            cursor
          }
        }
      }
      `;
    const variables = { ...pageArgs };
    return this.meta({ query, variables }).pipe(data('admin', 'users'));
  }

  getUserAsAdmin(id: string): Observable<User> {
    const query = `
      query ($id: String!) {
        admin {
          user(id: $id) {
            id
            email
            firstName
            lastName
            platformManager
            hideOrigin
            usageLimit {
              id
              name
            }
            usageLimitAssigned
          }
        }
      }
      `;
    const variables = { id };
    return this.meta({ query, variables }).pipe(data('admin', 'user'));
  }

  setDatasetPublished(datasetId: string, published: boolean): Observable<Response<Dataset>> {
    const query = `
      mutation ($datasetId: String!, $published: Boolean!) {
        onDataset(id: $datasetId) {
          setPublished(published: $published) {
            responseCode
            message
          }
        }
      }
    `;
    const variables = {
      datasetId,
      published
    };
    return this.meta({ query, variables }).pipe(data('onDataset', 'setPublished'), this.handleErrors());
  }

  setDatasetMemberRoles(datasetId: string, userId: string, roleIds: string[]): Observable<Response<Dataset>> {
    const input = {
      userId, roleIds
    }
    const query = `
    mutation ($datasetId: String!, $input: AssignRolesInput!) {
      onDataset(id: $datasetId) {
        assignRoles(input: $input) {
          responseCode
          message
        }
      }
    }
  `;
    const variables = {
      datasetId,
      input
    };
    return this.meta({ query, variables }).pipe(data('onDataset', 'assignRoles'), this.handleErrors());
  }

  setDatasetTeamRoles(datasetId: string, teamId: string, roleIds: string[]): Observable<Response<Dataset>> {
    const input = {
      teamId, roleIds
    }
    const query = `
    mutation ($datasetId: String!, $input: AssignTeamRolesInput!) {
      onDataset(id: $datasetId) {
        assignRolesAsTeam(input: $input) {
          responseCode
          message
        }
      }
    }
  `;
    const variables = {
      datasetId,
      input
    };
    return this.meta({ query, variables }).pipe(data('onDataset', 'assignRolesAsTeam'), this.handleErrors());
  }

  /**
   * Sets the name of an existing role on a dataset
   * @param datasetId 
   * @param roleId 
   * @param name 
   * @returns 
   */
  setDatasetRoleName(datasetId: string, roleId: string, name: string): Observable<Response<Role>> {
    const query = `
    mutation ($datasetId: String!, $roleId: String!, $name: String!) {
      onDataset(id: $datasetId) {
        onRole(id: $roleId) {
          setName(name: $name) {
            responseCode
            message
          }
        }
      }
    }
  `;
    const variables = {
      datasetId, roleId, name
    };
    return this.meta({ query, variables }).pipe(data('onDataset', 'onRole', 'setName'), this.handleErrors());
  }

  /**
   * Sets the description of an existing role on a dataset
   * @param datasetId 
   * @param roleId 
   * @param description 
   * @returns 
   */
  setDatasetRoleDescription(datasetId: string, roleId: string, description: string): Observable<Response<Role>> {
    const query = `
    mutation ($datasetId: String!, $roleId: String!, $description: String!) {
      onDataset(id: $datasetId) {
        onRole(id: $roleId) {
          setDescription(description: $description) {
            responseCode
            message
          }
        }
      }
    }
  `;
    const variables = {
      datasetId, roleId, description
    };
    return this.meta({ query, variables }).pipe(data('onDataset', 'onRole', 'setDescription'), this.handleErrors());
  }

  /**
   * Sets the permissions of an existing role on a dataset
   * @param datasetId 
   * @param roleId 
   * @param permissions 
   * @returns 
   */
  setDatasetRolePermissions(datasetId: string, roleId: string, permissions: Permission[]): Observable<Response<Role>> {
    const query = `
    mutation ($datasetId: String!, $roleId: String!, $permissions: [Permission!]!) {
      onDataset(id: $datasetId) {
        onRole(id: $roleId) {
          setPermissions(permissions: $permissions) {
            responseCode
            message
          }
        }
      }
    }
  `;
    const variables = {
      datasetId, roleId, permissions
    };
    return this.meta({ query, variables }).pipe(data('onDataset', 'onRole', 'setPermissions'), this.handleErrors());
  }

  /**
  * Sets the readFilter of an existing role on a dataset
  * @param datasetId 
  * @param roleId 
  * @param readFilter 
  * @returns 
  */
  setDatasetRoleReadFilter(datasetId: string, roleId: string, readFilter: Object): Observable<Response<Role>> {
    const query = `
      mutation ($datasetId: String!, $roleId: String!, $readFilter: JSON!) {
        onDataset(id: $datasetId) {
          onRole(id: $roleId) {
            setReadFilter(readFilter: $readFilter) {
              responseCode
              message
            }
          }
        }
      }
    `;
    const variables = {
      datasetId, roleId, readFilter
    };
    return this.meta({ query, variables }).pipe(data('onDataset', 'onRole', 'setReadFilter'), this.handleErrors());
  }

  setDatasetOpenData(datasetId: string, openData: boolean): Observable<Response<Dataset>> {
    const query = `
      mutation ($datasetId: String!, $openData: Boolean!) {
        onDataset(id: $datasetId) {
          setOpenData(openData: $openData) {
            responseCode
            message
          }
        }
      }
    `;
    const variables = {
      datasetId,
      openData
    };
    return this.meta({ query, variables }).pipe(data('onDataset', 'setOpenData'), this.handleErrors());
  }

  createDatasetInvite(datasetId: string, roleIds: string[], disallowTeams: boolean): Observable<Response<Invite>> {
    const query = `
      mutation ($datasetId: String!, $roleIds: [String], $disallowTeams: Boolean) {
        onDataset(id: $datasetId) {
          createInvite(input: {roleIds: $roleIds, disallowTeams: $disallowTeams }) {
            responseCode
            message
            item {
              id
            }
          }
        }
      }
    `;
    const variables = {
      datasetId,
      roleIds,
      disallowTeams
    }
    return this.meta({ query, variables }).pipe(data('onDataset', 'createInvite'), this.handleErrors());
  }

  createDatasetAccessRequest(datasetId: string, type: Permission[], message?: string): Observable<Response<AccessRequest>> {
    const input = {
      type,
      message
    }
    const query = `
      mutation ($datasetId: String!, $input: RequestAccessInput!) {
        onDataset(id: $datasetId) {
          requestAccess(input: $input) {
            responseCode
            message
          }
        }
      }
    `;
    const variables = {
      datasetId,
      input
    }
    return this.meta({ query, variables }).pipe(data('onDataset', 'requestAccess'), this.handleErrors());
  }

  createDatasetTeamAccessRequest(datasetId: string, teamId: string, type: Permission[], message?: string): Observable<Response<AccessRequest>> {
    const input = {
      type,
      message
    }
    const query = `
      mutation ($datasetId: String!, $teamId: String!, $input: RequestAccessInput!) {
        onDataset(id: $datasetId) {
          requestAccessAsTeam(teamId: $teamId, input: $input) {
            responseCode
            message
          }
        }
      }
    `;
    const variables = {
      datasetId,
      teamId,
      input
    }
    return this.meta({ query, variables }).pipe(data('onDataset', 'requestAccessAsTeam'), this.handleErrors());
  }

  removeDatasetAccessRequest(datasetId: string, arId: string): Observable<Response<AccessRequest>> {
    const query = `
      mutation ($datasetId: String!, $arId: String!) {
        onDataset(id: $datasetId) {
          onAccessRequest(id: $arId) {
            remove {
              responseCode
              message
            }
          }
        }
      }
    `;
    const variables = {
      datasetId,
      arId
    }
    return this.meta({ query, variables }).pipe(data('onDataset', 'onAccessRequest', 'remove'), this.handleErrors());
  }

  removeMyAccessRequest(arId: string): Observable<Response<AccessRequest>> {
    const query = `
      mutation ($arId: String!) {
        me {
          onAccessRequest(id: $arId) {
            remove {
              responseCode
              message
            }
          }
        }
      }
    `;
    const variables = {
      arId
    }
    return this.meta({ query, variables }).pipe(data('me', 'onAccessRequest', 'remove'), this.handleErrors());
  }

  createGroupInvite(datasetId: string, groupId: string): Observable<Response<Invite>> {
    const query = `
      mutation ($datasetId: String!, $groupId: String!) {
        onDataset(id: $datasetId) {
          createGroupInvite(input: {groupId: $groupId }) {
            responseCode
            message
            item {
              id
            }
          }
        }
      }
    `;
    const variables = {
      datasetId,
      groupId
    }
    return this.meta({ query, variables }).pipe(data('onDataset', 'createGroupInvite'), this.handleErrors());
  }

  listUsageLimits(pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<UsageLimit>> {
    const query = `
      query ($cursor: String, $limit: Int, $filter: UsageLimitFilter) {
        admin {
          usageLimits(cursor: $cursor, limit: $limit, filter: $filter) {
            items {
              id
              name
              description
              defaultLimit
            }
            cursor
          }
        }
      }
      `;
    const variables = { ...pageArgs };
    return this.meta({ query, variables }).pipe(data('admin', 'usageLimits'));
  }

  createUsageLimit(ul: UsageLimitInput): Observable<Response<UsageLimit>> {
    const query = `
      mutation ($ul: CreateUsageLimitInput!) {
        createUsageLimit(input: $ul) {
          responseCode
          message
          item {
            id
          }
        }
      }
    `;
    const variables = { ul };
    return this.meta({ query, variables }).pipe(data('createUsageLimit'), this.handleErrors());
  }

  getUsageLimit(id: string): Observable<UsageLimit> {
    return this.meta({
      query: `query ($id: String!) {
        admin {
          usageLimit(id: $id) {
            id
            name
            description
            defaultLimit
            values
          }
        }
      }`,
      variables: { id }
    }).pipe(data('admin', 'usageLimit'));
  }

  getUsageLimitDetails(): Observable<UsageLimitDetails> {
    return this.meta({
      query: `query{
        me {
          usageRemaining
          usageLimit {
            id
            name
            description
            values
          }
        }
      }`
    }).pipe(data('me'));
  }

  getAggregatedUsageLimitDetails(): Observable<AggregatedUsageLimitDetails> {
    return this.meta({
      query: `query{
        me {
          usageRemaining
          aggregatedUsageLimit {
            id
            name
            description
            values
          }
        }
      }`
    }).pipe(data('me'));
  }

  listUsagePlans(pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<UsagePlan>> {
    const query = `
      query ($cursor: String, $limit: Int, $filter: UsagePlanFilter) {
        admin {
          usagePlans(cursor: $cursor, limit: $limit, filter: $filter) {
            items {
              id
              name
              description
              defaultPlan
            }
            cursor
          }
        }
      }
      `;
    const variables = { ...pageArgs };
    return this.meta({ query, variables }).pipe(data('admin', 'usagePlans'));
  }

  createUsagePlan(up: UsagePlanInput): Observable<Response<UsagePlan>> {
    const query = `
      mutation ($up: CreateUsagePlanInput!) {
        createUsagePlan(input: $up) {
          responseCode
          message
          item {
            id
          }
        }
      }
    `;
    const variables = { up };
    return this.meta({ query, variables }).pipe(data('createUsagePlan'), this.handleErrors());
  }

  getUsagePlan(id: string, includeUsageLimits: boolean = false): Observable<UsagePlan> {
    return this.meta({
      query: `query ($id: String!, $includeUsageLimits: Boolean!) {
        admin {
          usagePlan(id: $id) {
            id
            name
            description
            defaultPlan
            maxUsers
            maxClients
            userUsageLimitAssigned
            userUsageLimit {
              id
              ...usageLimitFields
            }
            clientUsageLimitAssigned
            clientUsageLimit {
              id
              ...usageLimitFields
            }
          }
        }
      }
      fragment usageLimitFields on UsageLimit {
        name          @include(if: $includeUsageLimits)
        description   @include(if: $includeUsageLimits)
        defaultLimit  @include(if: $includeUsageLimits)
        values        @include(if: $includeUsageLimits)
      }
      `,
      variables: { id, includeUsageLimits }
    }).pipe(data('admin', 'usagePlan'));
  }

  getTeamUsagePlan(teamId: string, includeUsageLimits: boolean = false): Observable<UsagePlan> {
    return this.meta({
      query: `query ($teamId: String!, $includeUsageLimits: Boolean!) {
        team(id: $teamId) {
          usagePlan {
            id
            name
            description
            defaultPlan
            maxUsers
            maxClients
            userUsageLimitAssigned
            userUsageLimit {
              id
              ...usageLimitFields
            }
            clientUsageLimitAssigned
            clientUsageLimit {
              id
              ...usageLimitFields
            }
          }
        }
      }
      fragment usageLimitFields on UsageLimit {
        name          @include(if: $includeUsageLimits)
        description   @include(if: $includeUsageLimits)
        defaultLimit  @include(if: $includeUsageLimits)
        values        @include(if: $includeUsageLimits)
      }
      `,
      variables: { teamId, includeUsageLimits }
    }).pipe(data('team', 'usagePlan'));
  }

  removeUsagePlan(usagePlanId: string): Observable<Response<UsagePlan>> {
    const query = `
    mutation ($usagePlanId: String!) {
      onUsagePlan(id: $usagePlanId) {
        remove {
          responseCode
          message
        }
      }
    }`;
    const variables = { usagePlanId };
    return this.meta({ query, variables }).pipe(data('onUsagePlan', 'remove'), this.handleErrors());
  }

  removeUsageLimit(usageLimitId: string): Observable<Response<UsageLimit>> {
    const query = `
    mutation ($usageLimitId: String!) {
      onUsageLimit(id: $usageLimitId) {
        remove {
          responseCode
          message
        }
      }
    }`;
    const variables = { usageLimitId };
    return this.meta({ query, variables }).pipe(data('onUsageLimit', 'remove'), this.handleErrors());
  }

  editUsageLimit(id: string, input: UsageLimitInput): Observable<Response<UsageLimit>> {
    return this.meta({
      query: `mutation editUsageLimit($id: String!, $input: UpdateUsageLimitInput!) {
        onUsageLimit(id: $id) {
          update(input: $input) {
            responseCode
            message
          }
        }
      }`,
      variables: { id, input }
    }).pipe(data('onUsageLimit', 'update'), this.handleErrors());
  }

  markUsageLimitAsDefault(id: string): Observable<Response<UsageLimit>> {
    return this.meta({
      query: `mutation ($id: String!) {
        onUsageLimit(id: $id) {
          makeDefault {
            responseCode
            message
          }
        }
      }`,
      variables: { id }
    }).pipe(data('onUsageLimit', 'makeDefault'), this.handleErrors());
  }

  editUsagePlan(id: string, input: UsagePlanInput): Observable<Response<UsagePlan>> {
    return this.meta({
      query: `mutation editUsagePlan($id: String!, $input: UpdateUsagePlanInput!) {
        onUsagePlan(id: $id) {
          update(input: $input) {
            responseCode
            message
          }
        }
      }`,
      variables: { id, input }
    }).pipe(data('onUsagePlan', 'update'), this.handleErrors());
  }

  markUsagePlanAsDefault(id: string): Observable<Response<UsagePlan>> {
    return this.meta({
      query: `mutation ($id: String!) {
        onUsagePlan(id: $id) {
          makeDefault {
            responseCode
            message
          }
        }
      }`,
      variables: { id }
    }).pipe(data('onUsagePlan', 'makeDefault'), this.handleErrors());
  }

  listTeams(pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<Team>> {
    const query = `
      query ($cursor: String, $limit: Int, $filter: TeamFilter) {
        admin {
          teams(cursor: $cursor, limit: $limit, filter: $filter) {
            items {
              id
              name
              description
            }
            cursor
          }
        }
      }
      `;
    const variables = { ...pageArgs };
    return this.meta({ query, variables }).pipe(data('admin', 'teams'));
  }

  listMyTeams(pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<Team>> {

    const query = `
      query ($myId: String!, $cursor: String, $limit: Int, $filter: TeamFilter) {
        me {
          teams(cursor: $cursor, limit: $limit, filter: $filter) {
            items {
              id
              name
              description
              user (id: $myId) {
                manager
              }
            }
            cursor
          }
        }
      }
      `;
    return this.role.myId$().pipe(
      map(myId => ({ myId, ...pageArgs })),
      switchMap(variables => this.meta({ query, variables })),
      data('me', 'teams'));
  }

  listMyManagedTeams(pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<Team>> {
    const query = `
      query ($myId: String!, $cursor: String, $limit: Int) {
        me {
          teams(cursor: $cursor, limit: $limit, filter: {
            users: {
              manager: true,
              user: {
                id: $myId
              }
            }
          }) {
            items {
              id
              name
              description
            }
            cursor
          }
        }
      }
      `;
    return this.role.myId$().pipe(
      map(myId => ({ myId, ...pageArgs })),
      switchMap(variables => this.meta({ query, variables })),
      data('me', 'teams'));
  }

  createTeam(input: TeamInput): Observable<Response<Team>> {
    const variables = { input };
    const query = `mutation ($input: CreateTeamInput!) {
      createTeam(input: $input) {
        responseCode
        message
      }
    }`;
    return this.meta({
      query,
      variables
    }).pipe(data('createTeam'), this.handleErrors());
  }

  removeTeam(teamId: string): Observable<Response<Team>> {
    const variables = { id: teamId };
    const query = `mutation ($id: String!) {
      onTeam(id: $id) {
        remove {
          responseCode
          message
        }
      }
    }`;
    return this.meta({
      query,
      variables
    }).pipe(data('onTeam', 'remove'), this.handleErrors());
  }

  getTeamHeader(id: string): Observable<Team> {
    return this.meta({
      query: `
      query ($id: String!) {
          team(id: $id) {
            id
            name
            description
        }
      }`,
      variables: { id }
    }).pipe(data('team'));
  }

  getTeam(id: string, asManager: boolean = false): Observable<Team> {
    return this.meta({
      query: `
      query ($id: String!, $asManager: Boolean!) {
          team(id: $id) {
            id
            name
            description
            usersRemaining
            clientsRemaining
            usagePlan @include(if: $asManager) {
              id
              name
              description
              defaultPlan
            }
            usagePlanAssigned @include(if: $asManager) 
        }
      }`,
      variables: { id, asManager }
    }).pipe(data('team'));
  }

  getTeamDatasetAccess(teamId: string, datasetId: string): Observable<Role[]> {
    return this.meta({
      query: `
      query ($teamId: String!, $datasetId: String!) {
        team(id: $teamId) {
          membership(id: $datasetId) {
            roles {
              grant {
                permissions
              }
            }
          }
        }
      }`,
      variables: { teamId, datasetId }
    }).pipe(data('team', 'membership', 'roles'));
  }

  listTeamMembers(id: string, pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<TeamUser> {
    const query = `query ($id: String!, $cursor: String, $limit: Int, $filter: TeamUserFilter) {
      team(id: $id) {
        users(cursor: $cursor, limit: $limit, filter: $filter) {
          items {
            manager
            user {
              id
              email
              firstName
              lastName
            }
          }
          cursor
        }
      }
    }`
    const variables = {
      id,
      ...pageArgs
    };
    return this.meta({
      query,
      variables
    }).pipe(data('team', 'users'));
  }

  listTeamInvites(id: string, pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<TeamInvite>> {
    const query = `query ($id: String!, $cursor: String, $limit: Int) {
      team(id: $id) {
        invites(cursor: $cursor, limit: $limit) {
          items {
            id
            expiresInMs
          }
          cursor
        }
      }
    }`
    const variables = {
      id,
      ...pageArgs
    };
    return this.meta({
      query,
      variables
    }).pipe(data('team', 'invites'));
  }

  listDatasetInvites(id: string, pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<DatasetInvite>> {
    const query = `query ($id: String!, $cursor: String, $limit: Int) {
      dataset(id: $id) {
        invites(cursor: $cursor, limit: $limit) {
          items {
            id
            roles {
              id
              name
              grant {
                permissions
                readFilter
              }
            }
            expiresInMs
            disallowTeams
          }
          cursor
        }
      }
    }`
    const variables = {
      id,
      ...pageArgs
    };
    return this.meta({
      query,
      variables
    }).pipe(data('dataset', 'invites'));
  }

  createTeamInvite(id: string): Observable<Response<TeamInvite>> {
    const query = `mutation ($id: String!) {
      onTeam(id: $id) {
        createInvite {
          responseCode
          message
          item {
            id
            expiresInMs
          }
        }
      }
    }`
    const variables = {
      id,
    };
    return this.meta({
      query,
      variables
    }).pipe(data('onTeam', 'createInvite'), this.handleErrors());

  }

  /**
   * Returns a team object with the invite merged into it.
   */
  getTeamInvite(teamId: string, inviteId: string,): Observable<Team> {
    const query = `
      query ($teamId: String!, $inviteId: String!) {
        team(id: $teamId) {
          id
          name
          description
          usersRemaining
          invite(id: $inviteId) {
            id
            expiresInMs
          }
        }
      }
    `;
    const variables = {
      teamId, inviteId
    }
    return this.meta({ query, variables }).pipe(data('team'));
  }

  acceptTeamInvite(teamId: string, inviteId: string): Observable<Response<TeamInvite>> {
    const query = `
      mutation ($teamId: String!, $inviteId: String!) {
        onTeam(id: $teamId) {
          onInvite(id: $inviteId) {
            accept {
              responseCode
              message
            }
          }
        }
      }
    `;
    const variables = {
      teamId, inviteId
    }
    return this.meta({ query, variables }).pipe(data('onTeam', 'onInvite', 'accept'), this.handleErrors());
  }

  revokeTeamInvite(teamId: string, inviteId: string): Observable<Response<TeamInvite>> {
    const query = `
      mutation ($teamId: String!, $inviteId: String!) {
        onTeam(id: $teamId) {
          onInvite(id: $inviteId) {
            revoke {
              responseCode
              message
            }
          }
        }
      }
    `;
    const variables = {
      teamId, inviteId
    }
    return this.meta({ query, variables }).pipe(data('onTeam', 'onInvite', 'revoke'), this.handleErrors());
  }

  removeTeamMember(teamId: string, memberId: string): Observable<Response<Team>> {
    const query = `
      mutation ($teamId: String!, $memberId: String!) {
        onTeam(id: $teamId) {
          onTeamUser(id: $memberId) {
            remove {
              responseCode
              message
            }            
          }
        }
      }
    `;
    const variables = {
      teamId, memberId
    }
    return this.meta({ query, variables }).pipe(data('onTeam', 'onTeamUser', 'remove'), this.handleErrors());
  }

  getTeamTiles(teamId: string): Observable<TeamTiles> {
    const query = `
      query ($teamId: String!) {
        team(id: $teamId) {
          users { count }
          clients { count }
          memberships { count }
          exports { count }
          activeStreams { count }
        }
      }
    `;
    const variables = { teamId };
    return this.meta({ query, variables }).pipe(
      data('team'),
      map((obj: any) => ({
        members: obj.users.count,
        clients: obj.clients.count,
        datasets: obj.memberships.count,
        exports: obj.exports.count,
        streams: obj.activeStreams.count,
      })));
  }

  setTeamName(teamId: string, name: string): Observable<Response<Team>> {
    const query = `
      mutation ($teamId: String!, $name: String!) {
        onTeam(id: $teamId) {
          setName(name: $name) {
            responseCode
            message
          }
        }
      }
    `;
    const variables = {
      teamId, name
    }
    return this.meta({ query, variables }).pipe(data('onTeam', 'setName'), this.handleErrors());
  }

  setTeamDescription(teamId: string, description: string): Observable<Response<Team>> {
    const query = `
      mutation ($teamId: String!, $description: String) {
        onTeam(id: $teamId) {
          setDescription(description: $description) {
            responseCode
            message
          }
        }
      }
    `;
    const variables = {
      teamId, description
    }
    return this.meta({ query, variables }).pipe(data('onTeam', 'setDescription'), this.handleErrors());
  }

  setTeamManager(teamId: string, userId: string, manager: boolean): Observable<Response<TeamUser>> {
    const query = `
      mutation ($teamId: String!, $userId: String!, $manager: Boolean!) {
        onTeam(id: $teamId) {
          onTeamUser(id: $userId) {
            setManager(teamManager: $manager) {
              responseCode
              message
            }            
          }
        }
      }
    `;
    const variables = {
      teamId, userId, manager
    }
    return this.meta({ query, variables }).pipe(data('onTeam', 'onTeamUser', 'setManager'), this.handleErrors());
  }

  setUserUsageLimit(userId: string, usageLimitId: string): Observable<Response<User>> {
    const input = {
      usageLimitId
    };
    const query = `
      mutation ($userId: String!, $input: UpdateUserInput!) {
        onUser(id: $userId) {
          update(input: $input) {
            responseCode
            message
          }
        }
      }
    `;
    const variables = {
      userId, input
    }
    return this.meta({ query, variables }).pipe(data('onUser', 'update'), this.handleErrors());
  }

  setTeamUsagePlan(teamId: string, usagePlanId: string): Observable<Response<Team>> {
    const query = `
      mutation ($teamId: String!, $usagePlanId: String) {
        onTeam(id: $teamId) {
          setUsagePlan(usagePlanId: $usagePlanId) {
            responseCode
            message
          }
        }
      }
    `;
    const variables = {
      teamId, usagePlanId
    }
    return this.meta({ query, variables }).pipe(data('onTeam', 'setUsagePlan'), this.handleErrors());
  }

  listDatasetRoles(datasetId: string, pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<Role>> {
    const query = `query ($datasetId: String!, $cursor: String, $limit: Int, $filter: RoleFilter) {
      dataset(id: $datasetId) {
        roles(cursor: $cursor, limit: $limit, filter: $filter) {
          items {
            id
            name
            description
            grant {
              permissions
              readFilter
            }
          }
          cursor
        }
      }
    }`
    const variables = {
      datasetId,
      ...pageArgs
    };
    return this.meta({
      query,
      variables
    }).pipe(data('dataset', 'roles'));
  }

  getDatasetRole(datasetId: string, roleId: string): Observable<Role> {
    const query = `query ($datasetId: String!, $roleId: String!) {
      dataset(id: $datasetId) {
        role(id: $roleId) {
          id
          name
          description
          grant {
            permissions
            readFilter
          }
        }
      }
    }`
    const variables = {
      datasetId,
      roleId
    };
    return this.meta({
      query,
      variables
    }).pipe(data('dataset', 'role'));
  }

  listDatasetRoleUsers(datasetId: string, roleId: string, pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<User>> {
    const query = `query ($datasetId: String!, $roleId: String!, $cursor: String, $limit: Int, $filter: UserFilter) {
      dataset(id: $datasetId) {
        role(id: $roleId) {
          users(cursor: $cursor, limit: $limit, filter: $filter) {
            items {
              id firstName lastName email
            }
            cursor
          }
        }
      }
    }`
    const variables = {
      datasetId,
      roleId,
      ...pageArgs
    };
    return this.meta({
      query,
      variables
    }).pipe(data('dataset', 'role', 'users'));
  }

  listDatasetRoleTeams(datasetId: string, roleId: string, pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<Team>> {
    const query = `query ($datasetId: String!, $roleId: String!, $cursor: String, $limit: Int, $filter: TeamFilter) {
      dataset(id: $datasetId) {
        role(id: $roleId) {
          teams(cursor: $cursor, limit: $limit, filter: $filter) {
            items {
              id name description
            }
            cursor
          }
        }
      }
    }`
    const variables = {
      datasetId,
      roleId,
      ...pageArgs
    };
    return this.meta({
      query,
      variables
    }).pipe(data('dataset', 'role', 'teams'));
  }

  createRole(datasetId: string, input: Role): Observable<Response<Role>> {
    const variables = {
      datasetId,
      input
    };
    const query = `
    mutation ($datasetId: String!, $input: CreateRoleInput!) {
      onDataset(id: $datasetId) {
        createRole(input: $input) {
          responseCode
          message
        }
      }
    }`;
    return this.meta({
      query,
      variables
    }).pipe(data('onDataset', 'createRole'), this.handleErrors());
  }

  removeRole(datasetId: string, roleId: string): Observable<Response<Role>> {
    const variables = {
      datasetId,
      roleId
    };
    const query = `
    mutation ($datasetId: String!, $roleId: String!) {
      onDataset(id: $datasetId) {
        onRole(id: $roleId) {
          remove {
            responseCode
            message
          }
        }
      }
    }`;
    return this.meta({
      query,
      variables
    }).pipe(data('onDataset', 'onRole', 'remove'), this.handleErrors());
  }

  removeMyData(myId: string, input: DataRemovalRequestInput): Observable<Response<User>> {
    const variables = { myId, input };
    const query = `
    mutation ($myId: String!, $input: DataRemovalRequestInput!) {
      onUser(id: $myId) {
        requestDataRemoval(input: $input) {
          responseCode
          message
        }
      }
    }`;
    return this.meta({
      query,
      variables
    }).pipe(data('onUser', 'requestDataRemoval'), this.handleErrors());
  }

  removeClientData(clientId: string, input: DataRemovalRequestInput): Observable<Response<Client>> {
    const variables = { clientId, input };
    const query = `
    mutation ($clientId: String!, $input: DataRemovalRequestInput!) {
      onClient(id: $clientId) {
        requestDataRemoval(input: $input) {
          responseCode
          message
        }
      }
    }`;
    return this.meta({
      query,
      variables
    }).pipe(data('onClient', 'requestDataRemoval'), this.handleErrors());
  }

  removeTeamClientData(teamId: string, clientId: string, input: DataRemovalRequestInput): Observable<Response<Client>> {
    const variables = { teamId, clientId, input };
    const query = `
    mutation ($teamId: String!, $clientId: String!, $input: DataRemovalRequestInput!) {
      onTeam(id: $teamId) {
        onClient(id: $clientId) {
          requestDataRemoval(input: $input) {
            responseCode
            message
          }
        }
      }
    }`;
    return this.meta({
      query,
      variables
    }).pipe(data('onTeam', 'onClient', 'requestDataRemoval'), this.handleErrors());
  }

  /** ISSUES APIS*/

  createPersonalIssue(issue: IssueInput): Observable<void> {
    return this.http.post<void>(this.path('/issues/personal'), issue);
  }

  addPersonalIssueComment(id: string, comment: IssueComment, close: boolean = false): Observable<void> {
    return this.http.post<void>(this.path(`/issues/personal/${id}/activity?close=${close}`), comment);
  }

  resolvePersonalIssue(id: string): Observable<void> {
    return this.http.post<void>(this.path(`/issues/personal/${id}/activity?close=true`), {});
  }

  removePersonalIssue(issueId: string): Observable<void> {
    return this.http.delete<void>(this.path(`/issues/personal/${issueId}`));
  }

  removePersonalIssueComment(issueId: string, commentId: string): Observable<void> {
    return this.http.delete<void>(this.path(`/issues/personal/${issueId}/activity/${commentId}`));
  }

  getPersonalIssue(id: string): Observable<Issue> {
    return this.http.get<Issue>(this.path(`/issues/personal/${id}`));
  }

  listPersonalIssues(pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<Issue>> {
    const path = this.path('/issues/personal', pageArgs)
    return this.http.get<Page<Issue>>(path);
  }

  listPersonalIssueComments(id: string): Observable<IssueActivity[]> {

    return this.http.get<IssueActivity[]>(this.path(`/issues/personal/${id}/activity`,));
  }


  listAllIssues(pageArgs: PageArgs = { cursor: null, limit: 25 }): Observable<Page<Issue>> {
    return this.http.get<Page<Issue>>(this.path('/issues/all', pageArgs));
  }

  getIssue(id: string): Observable<Issue> {
    return this.http.get<Issue>(this.path(`/issues/all/${id}`));
  }

  listIssueComments(id: string): Observable<IssueActivity[]> {
    return this.http.get<IssueActivity[]>(this.path(`/issues/all/${id}/activity`));
  }

  addIssueComment(id: string, comment: IssueComment | null, changeState: IssueState | null, internal: boolean = false): Observable<void> {
    const body = { comment, changeState, internal };
    return this.http.post<void>(this.path(`/issues/all/${id}/activity`), body);
  }

  updatePersonalIssueComment(ticketId: string, commentId: string, comment: string): Observable<void> {
    return this.http.put<void>(this.path(`/issues/personal/${ticketId}/activity/${commentId}`), { comment });
  }
  updateIssueComment(ticketId: string, commentId: string, comment: string): Observable<void> {
    return this.http.put<void>(this.path(`/issues/all/${ticketId}/activity/${commentId}`), { comment });
  }

  resolveIssue(id: string): Observable<void> {
    return this.http.post<void>(this.path(`/issues/all/${id}/activity?close=true`), {});
  }

  removeIssue(issueId: string): Observable<void> {
    return this.http.delete<void>(this.path(`/issues/all/${issueId}`));
  }

  removeIssueComment(issueId: string, commentId: string): Observable<void> {
    return this.http.delete<void>(this.path(`/issues/all/${issueId}/activity/${commentId}`));
  }

  editIssue(issueId: string, edit: IssueUpdate): Observable<void> {
    return this.http.put<void>(this.path(`/issues/all/${issueId}`), edit);
  }

  editPersonalIssue(issueId: string, edit: IssueUpdate): Observable<void> {
    return this.http.put<void>(this.path(`/issues/personal/${issueId}`), edit);
  }

  getAdvancedStatus(): Observable<AdvancedStatus[]> {
    return this.http.get<AdvancedStatus[]>(this.path('/monitor'));
  }

}