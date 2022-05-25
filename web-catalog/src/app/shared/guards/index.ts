import { AdminGuard } from "./admin.guard";
import { AuthGuard } from "./auth.guard";
import { OpenGuard } from "./open.guard";
import { DatasetGuard } from "./dataset.guard";
import { TeamGuard } from "./team.guard";

export const guards: any[] = [
    AdminGuard,
    AuthGuard,
    OpenGuard,
    DatasetGuard,
    TeamGuard
];

export * from "./admin.guard";
export * from "./auth.guard";
export * from "./open.guard";
export * from "./dataset.guard";
export * from "./team.guard";