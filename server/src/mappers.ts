import type { Role } from './auth.js';

export interface UserDto {
  id: number;
  login: string;
  fullName: string;
  role: Role;
  groupId: number | null;
  groupName: string | null;
  avatar: string | null;
  isActive: boolean;
  createdAt: string;
}

interface UserJoinRow {
  id: number;
  login: string;
  role: Role;
  full_name: string;
  group_id: number | null;
  group_name: string | null;
  avatar: string | null;
  is_active: number;
  created_at: string;
}

export function mapUser(row: UserJoinRow): UserDto {
  return {
    id: row.id,
    login: row.login,
    fullName: row.full_name,
    role: row.role,
    groupId: row.group_id,
    groupName: row.group_name,
    avatar: row.avatar ?? null,
    isActive: row.is_active === 1,
    createdAt: row.created_at,
  };
}

export const USER_SELECT = `
  SELECT u.id, u.login, u.password_hash, u.role, u.full_name, u.group_id, u.avatar,
         u.is_active, u.created_at, g.name AS group_name
  FROM users u
  LEFT JOIN groups g ON g.id = u.group_id
`;
