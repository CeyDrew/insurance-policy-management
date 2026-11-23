export type Role = 'ADMIN' | 'AGENT' | 'CLAIM_HANDLER';

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Yönetici',
  AGENT: 'Acente',
  CLAIM_HANDLER: 'Hasar Uzmanı',
};

export interface User {
  id: number;
  username: string;
  fullName: string;
  email: string;
  role: Role;
  active: boolean;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}

export interface Session {
  accessToken: string;
  refreshToken: string;
  user: User;
}
