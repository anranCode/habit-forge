<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showSuccessToast } from 'vant'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()

const username = ref('')
const password = ref('')
const loading = ref(false)

async function onSubmit() {
  if (!username.value || !password.value) return
  loading.value = true
  try {
    await userStore.login({ username: username.value, password: password.value })
    showSuccessToast('欢迎回来！')
    const redirect = (route.query.redirect as string) || '/home'
    router.replace(redirect)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-hero">
      <div class="logo"><span class="deco">🔥 </span>HabitForge</div>
      <div class="slogan">微小的变化，显著的结果</div>
    </div>

    <div class="auth-body">
      <van-form @submit="onSubmit">
        <van-cell-group inset>
          <van-field
            v-model="username"
            name="username"
            label="用户名"
            placeholder="请输入用户名"
            :rules="[{ required: true, message: '请输入用户名' }]"
          />
          <van-field
            v-model="password"
            type="password"
            name="password"
            label="密码"
            placeholder="请输入密码"
            :rules="[{ required: true, message: '请输入密码' }]"
          />
        </van-cell-group>
        <div class="auth-submit">
          <van-button round block type="primary" native-type="submit" :loading="loading" color="#ff7a00">
            登 录
          </van-button>
        </div>
      </van-form>

      <div class="auth-footer">
        还没有账号？
        <router-link to="/register" class="link">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.auth-page {
  min-height: 100vh;
  background: linear-gradient(160deg, #1a1a2e 0%, #16213e 45%, #f6f7fb 45.2%);
}

.auth-hero {
  text-align: center;
  padding: 64px 20px 56px;
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

/* 桌面端：移动端那条 `45% → 45.2%` 的硬斜切是按手机竖向比例画的，
   铺到 1440px 宽屏会被抻成一张斜着一半的怪图（下方浅色区本来是给表单当底的，
   宽屏下它变成一条横贯全屏的分界线）。桌面端换成满屏深色底 + 居中白卡片，
   斜切在新底色里被整条覆盖掉。移动端样式一行不动。 */
@media (min-width: #{$bp-desktop}) {
  .auth-page {
    display: flex;
    flex-direction: column;
    justify-content: center; // 英雄区 + 卡片整块在视口里垂直居中
    padding: 48px 20px;
    // 覆盖移动端那条硬斜切 —— 同一属性、整条替换
    background: linear-gradient(160deg, #1a1a2e 0%, #16213e 55%, #0f3460 100%);
  }

  .auth-hero {
    padding: 0 20px 24px;
  }

  .auth-body {
    width: 100%;
    padding: 28px 24px 32px;
    background: $bg-card;
    border-radius: 16px;
    box-shadow: 0 18px 48px rgba(0, 0, 0, 0.28);

    // inset 单元格组在移动端是"浮在浅色页底上的白卡"；桌面端外层已经是白卡了，
    // 再留 16px 外边距只会让表单往里缩一圈，去掉，改用浅灰底把输入区框出来
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
