import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { GraphQ, Permission } from '@shared/model';
import { data, hideErrors } from '@shared/utils';
import { Observable, of, Subject } from 'rxjs';
import { catchError, map, shareReplay, switchMap, takeUntil } from 'rxjs/operators';
import { AuthService } from './auth.service';

@Injectable({
  providedIn: 'root'
})
export class RoleService {
  private myIdCache: Observable<string>;

  private uri: string;

  private unsub$: Subject<void> = new Subject();



  constructor(
    private http: HttpClient,
    private auth: AuthService
  ) {
    const config = this.auth.getConfig();
    this.uri = config.oblxHost + config.oblxApiPrefix;
  }

  /**
   * Clears the cache, forcing all requests to be executed again.
   */
  clearCache() {
    // this.isAdminCache = null;
    this.unsub$.next();
  }

  /**
   * Return whether the user has admin rights.
   */
  isAdmin$(): Observable<boolean> {
    return this.qAdminRights().pipe(shareReplay(1))
  }

  /**
   * Return whether the user has manage rights for this team.
   * @param teamId Id of the team
   */
  isTeamManager$(teamId: string): Observable<boolean> {
    return this.qIsTeamManager(teamId).pipe(shareReplay(1));
  }

  /**
   * Return whether the user is member of this dataset
   */
  isDatasetMember$(datasetId: string, adminToo: boolean = true): Observable<boolean> {
    return this.qDatasetRights(datasetId).pipe(
      hideErrors(),
      map(perms => (perms != null)),
      switchMap(this.adminToo(adminToo))
    );
  }

  /**
   * Return all actual permissions the user has on this dataset
   * @param datasetId DatasetdatasetId
   */
  getDatasetRights$(datasetId: string): Observable<Permission[]> {
    return this.qDatasetRights(datasetId).pipe(shareReplay(1));
  }

  /**
   * Return whether the user can READ this dataset.
   * @param datasetId DatasetdatasetId
   * @param adminToo _Default to true._ If user is admin, return true.  
   */
  canReadDataset$(datasetId: string, adminToo: boolean = true): Observable<boolean> {
    return this.getDatasetRights$(datasetId).pipe(switchMap(this.canDo('READ', adminToo)));
  }

  /**
   * Return whether the user can WRITE this dataset.
   * @param datasetId DatasetdatasetId
   * @param adminToo _Default to true._ If user is admin, return true.  
   */
  canWriteDataset$(datasetId: string, adminToo: boolean = true): Observable<boolean> {
    return this.getDatasetRights$(datasetId).pipe(switchMap(this.canDo('WRITE', adminToo)));
  }

  /**
   * Return whether the user can MANAGE this dataset.
   * @param datasetId DatasetId
   * @param adminToo _Default to true._ If user is admin, return true.  
   */
  canManageDataset$(datasetId: string, adminToo: boolean = true): Observable<boolean> {
    return this.getDatasetRights$(datasetId).pipe(switchMap(this.canDo('MANAGE', adminToo)));
  }

  canManageTeam$(teamId: string, adminToo: boolean = true): Observable<boolean> {
    return teamId ? this.qIsTeamManager(teamId).pipe(shareReplay(1), switchMap(this.adminToo(adminToo))) : of(false);
  }

  /**
   * Get myId from cache, else request online.
   */
  myId$(): Observable<string> {
    if (this.myIdCache == null) {
      this.myIdCache = this.qMyId().pipe(shareReplay(1));
    }
    return this.myIdCache;
  }


  /** INTERNAL HELPER METHODS */

  private graphql(): string {
    return this.uri + '/catalog/graphql';
  }

  /**
   * Post a metadata query
   * @param query Graphql query
   */
  private meta(query: GraphQ): Observable<Object[]> {
    return this.http.post<Object[]>(this.graphql(), query)
  }

  /**
  * Use with switchMap to check a permission list for the given permission.
  * If present return Observable<true> if not or permission list is empty return Observable<false>.
  * @param includeAdmin If admin, also return true, else only use permission check.
  */
  private canDo(permission: Permission, includeAdmin: boolean) {
    return (perms: Permission[]) => {
      const permPresent = perms?.includes(permission) || false;
      return includeAdmin ? this.isAdmin$().pipe(switchMap(admin => of(admin || permPresent))) : of(permPresent);
    };
  }

  /**
  * Use with switchMap to check compound a boolean with admin rights.
  * If ok return Observable<true> if not return Observable<false>.
  * @param includeAdmin If admin, also return true, else only use normal check.
  */
  private adminToo(includeAdmin: boolean) {
    return (ok: boolean) => includeAdmin ? this.isAdmin$().pipe(switchMap(admin => of(admin || ok))) : of(ok);
  }

  /** INTERNAL CALLS */

  /**
   * Do you have admin rights?
   * @returns 
   */
  private qAdminRights(): Observable<boolean> {
    return this.auth.authReady$.pipe(
      switchMap(_ => this.meta({ query: `{me{platformManager}}` }).pipe(data<boolean>('me', 'platformManager'))),
      takeUntil(this.unsub$));
  }

  /**
   * What permissions do you have on the given dataset?
   * @param datasetId Dataset id
   * @returns 
   */
  private qDatasetRights(datasetId: string): Observable<Permission[]> {
    const query = `query ($datasetId: String!) {
        me {
          membership(id: $datasetId) {
            aggregatedGrant {
              permissions
            }
          }
        }
      }`
    const variables = { datasetId };
    return this.auth.authReady$.pipe(
      switchMap(_ => this.meta({ query, variables })),
      data<Permission[]>('me', 'membership', 'aggregatedGrant', 'permissions'),
      takeUntil(this.unsub$));
  }

  /**
   * Are you manager of this team?
   * @param teamId 
   * @returns 
   */
  private qIsTeamManager(teamId: string): Observable<boolean> {
    const query = `query ($tid: String!, $uid: String!) {
        me {
          team(id: $tid) {
            user(id: $uid) {
              manager
            }
          }
        }
      }`;

    return this.auth.authReady$.pipe(
      switchMap(_ => this.myId$()),
      map(uid => ({ tid: teamId, uid })),
      switchMap(variables => this.meta({ query, variables })),
      data<any>('me', 'team', 'user', 'manager'),
      map(res => res === undefined ? false : res),
      catchError((obs, err) => of(false)),
      takeUntil(this.unsub$));
  }

  /**
   * What is my user id?  
   * **USE myId$ for cached version1**
   * @returns 
   */
  private qMyId(): Observable<string> {
    return this.meta({
      query: `query { me { id } }`
    }).pipe(data('me', 'id'));
  }
}


