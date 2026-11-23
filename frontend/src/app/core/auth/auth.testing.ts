import { AuthResponse, Role, User } from './auth.models';

export function testUser(role: Role = 'AGENT'): User {
  return {
    id: 1,
    username: role.toLowerCase(),
    fullName: 'Test Kullanıcı',
    email: 'test@demo.local',
    role,
    active: true,
  };
}

export function authResponse(role: Role = 'AGENT', suffix = '1'): AuthResponse {
  return {
    accessToken: `access-${suffix}`,
    refreshToken: `refresh-${suffix}`,
    tokenType: 'Bearer',
    expiresIn: 900,
    user: testUser(role),
  };
}
