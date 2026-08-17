export interface UserProfile {
  id: string
  username: string
  email: string
  identityGoal?: string
  avatarUrl?: string
  points: number
  level: number
  createdAt?: string
  lastLoginAt?: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
  identityGoal?: string
}

export interface LoginResponse {
  token: string
  user: UserProfile
}

export interface IdentityUpdate {
  identityGoal?: string
  avatarUrl?: string
}
