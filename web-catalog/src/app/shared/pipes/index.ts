import { AgoPipe } from "./ago.pipe";
import { CheckmarkPipe } from "./checkmark.pipe";
import { DurationPipe } from "./duration.pipe";
import { FilesizePipe } from "./filesize.pipe";
import { HasPipe } from "./has.pipe";
import { NrPipe } from "./nr.pipe";
import { ResourcePipe } from "./resource.pipe";
import { UserPipe } from "./user.pipe";
import { MapListPipe } from "./map-list.pipe";
import { SafePipe } from "./safe.pipe";
import { Uid2avatarPipe } from './uid2avatar.pipe';

export const pipes: any[] = [
    AgoPipe,
    CheckmarkPipe,
    DurationPipe,
    FilesizePipe,
    HasPipe,
    NrPipe,
    ResourcePipe,
    UserPipe,
    MapListPipe,
    SafePipe,
    Uid2avatarPipe
];

export * from "./ago.pipe";
export * from "./checkmark.pipe";
export * from "./duration.pipe";
export * from "./filesize.pipe";
export * from "./has.pipe";
export * from "./nr.pipe";
export * from "./resource.pipe";
export * from "./user.pipe";
export * from "./map-list.pipe";
export * from "./safe.pipe";
export * from './uid2avatar.pipe';

