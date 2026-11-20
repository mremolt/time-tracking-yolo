export type Role = 'EMPLOYEE' | 'PROJECT_LEAD' | 'BACKOFFICE_ADMIN' | 'MANAGEMENT';

export interface User {
  id: number; email: string; firstName: string; lastName: string; personnelNumber?: string; active: boolean;
  roles: Role[]; teamId?: number; teamName?: string; entryDate?: string; exitDate?: string; language: 'de' | 'en';
  defaultProjectId?: number; preferredView: 'day' | 'week'; notifyBudget: boolean; openingBalanceMinutes: number;
  openingBalanceDate?: string; invitePending: boolean; locked: boolean;
}
export interface Msg { key: string; params: Record<string, string | number>; }
export interface Break { start: string; end?: string | null; }
export interface WorkDay { id: number; date: string; start: string; end?: string | null; breaks: Break[]; running: boolean; paused: boolean; }
export interface Entry {
  id: number; userId: number; date: string; projectId: number; activityId: number; start?: string | null; end?: string | null;
  minutes: number; description?: string; running: boolean;
}
export interface Day {
  date: string; targetMinutes: number; baseTargetMinutes: number; workMinutes: number; breakMinutes: number; projectMinutes: number;
  holidayDe?: string; holidayEn?: string; absence?: 'VACATION' | 'SICK' | 'HOLIDAY' | 'OTHER'; absenceHalf: boolean;
  workDay?: WorkDay | null; entries: Entry[]; warnings: Msg[]; hints: Msg[];
}
export interface Status { today: string; workDay?: WorkDay | null; projectTimer?: Entry | null; balanceMinutes: number; }
export interface BookableProject { id: number; code: string; name: string; favorite: boolean; recent: boolean; }
export interface Simple { id: number; name: string; }
export interface Table { title: string; headers: string[]; rows: string[][]; totals: string[]; notes: string[]; }
export interface Notification { id: number; messageKey: string; params: string; createdAt: string; read: boolean; }
