import request from '@/api/request'
import type { LoginRequest, RegisterRequest, LoginResponse, UserProfile, IdentityUpdate } from '@/types/user'

export const apiRegister = (data: RegisterRequest) =>
  request.post<never, LoginResponse>('/auth/register', data)

export const apiLogin = (data: LoginRequest) =>
  request.post<never, LoginResponse>('/auth/login', data)

export const apiLogout = () => request.post<never, void>('/auth/logout')

export const apiMe = () => request.get<never, UserProfile>('/auth/me')

export const apiUpdateProfile = (data: IdentityUpdate) =>
  request.put<never, UserProfile>('/user/profile', data)
