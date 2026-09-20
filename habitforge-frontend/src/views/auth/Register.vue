<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const router = useRouter()

const form = ref({
  username: '',
  email: '',
  password: '',
  identityGoal: ''
})
const loading = ref(false)

async function onSubmit() {
  loading.value = true
  try {
    await userStore.register({
      username: form.value.username,
      email: form.value.email,
      password: form.value.password,
      identityGoal: form.value.identityGoal || undefined
    })
    showSuccessToast('注册成功，开始锻造习惯吧！')
    router.replace('/home')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-hero">
      <div class="logo"><span class="deco">🔥 </span>HabitForge</div>
      <div class="slogan">决定你想成为谁，然后用小赢证明自己</div>
    </div>

    <div class="auth-body">
      <van-form @submit="onSubmit">
        <van-cell-group inset>
          <van-field
            v-model="form.username"
            label="用户名"
            placeholder="2-50 位字母/数字/下划线/中文"
            :rules="[{ required: true, message: '请输入用户名' }]"
          />
          <van-field
            v-model="form.email"
            label="邮箱"
            placeholder="用于登录"
            :rules="[
              { required: true, message: '请输入邮箱' },
              { pattern: /^[^\s@]+@[^\s@]+\.[^\s@]+$/, message: '邮箱格式不正确' }
            ]"
          />
          <van-field
            v-model="form.password"
            type="password"
            label="密码"
            placeholder="至少 6 位"
            :rules="[
              { required: true, message: '请输入密码' },
              { validator: (v: string) => v.length >= 6, message: '密码至少 6 位' }
            ]"
          />
          <van-field
            v-model="form.identityGoal"
            label="身份设定"
            placeholder="选填：我想成为一个…"
          />
        </van-cell-group>
        <div class="auth-submit">
          <van-button round block type="primary" native-type="submit" :loading="loading" color="#ff7a00">
            注 册
          </van-button>
        </div>
      </van-form>

      <div class="auth-footer">
        已有账号？
        <router-link to="/login" class="link">去登录</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.auth-page {
  min-height: 100vh;
  background: linear-gradient(160deg, #1a1a2e 0%, #16213e 45%, $bg-page 45.2%);
}

.auth-hero {
  text-align: center;
  padding: 56px 20px 48px;
  color: #fff;

  .logo {
    font-size: 30px;
    font-weight: 800;
    letter-spacing: 1px;
  }

  .slogan {
    margin-top: 10px;
    opacity: 0.75;
    font-size: 14px;
  }
}

.auth-body {
  max-width: 420px;
  margin: 0 auto;
  padding: 8px 0 40px;
}

.auth-submit {
  margin: 24px 16px 0;
}

.auth-footer {
  text-align: center;
  margin-top: 20px;
  font-size: 14px;
  color: $text-light;

  .link {
    color: $primary;
    font-weight: 600;
    text-decoration: none;
  }
}

/* 桌面端：见 Login.vue 同名注释 —— 移动端那条 45%→45.2% 的硬斜切是给手机竖屏
   比例画的，宽屏下会被抻成一条横贯全屏的分界线。桌面端整条覆盖成满屏深色底，
   表单装进居中白卡片。移动端样式一行不动。 */
@media (min-width: #{$bp-desktop}) {
  .auth-page {
    display: flex;
    flex-direction: column;
    justify-content: center;
    padding: 48px 20px;
    background: linear-gradient(160deg, #1a1a2e 0%, #16213e 55%, #0f3460 100%);
  }

  .auth-hero {
    padding: 0 20px 24px;
  }

  .auth-body {
    width: 100%;
    padding: 28px 24px 32px;
    background: $bg-card;
    border-radius: $radius-md; // 原是 16px —— 对话框属于「面板」那一档
    // 投影保留，理由同 Login.vue（深色渐变上唯一能表达"浮起"的手段）
    box-shadow: 0 18px 48px rgba(0, 0, 0, 0.28);

    // 理由同 Login.vue
    :deep(.van-cell-group--inset) {
      margin: 0;
      background: #f7f8fa;
    }
  }

  .auth-submit {
    margin: 24px 0 0;
  }
}
</style>
