/** MinIO objectKey -> 可展示 URL（经 nginx/vite 代理到 habitforge-images 桶） */
export const imageUrl = (objectKey: string): string => `/images/${objectKey}`
