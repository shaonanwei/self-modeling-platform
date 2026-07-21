<template>
  <div class="login-container">
    <div class="login-background">
      <div class="bg-decoration bg-1"></div>
      <div class="bg-decoration bg-2"></div>
      <div class="bg-decoration bg-3"></div>
    </div>
    
    <div class="login-card">
      <div class="login-header">
        <div class="logo-icon">
          <el-icon size="40"><Box /></el-icon>
        </div>
        <h1 class="title">自助建模平台</h1>
        <p class="subtitle">自助建模 · 数据驱动</p>
      </div>

      <el-form
        ref="formRef"
        :model="loginForm"
        :rules="rules"
        class="login-form"
        @submit.prevent="handleLogin"
      >
        <el-form-item prop="username">
          <div class="input-wrapper">
            <el-icon class="input-icon"><User /></el-icon>
            <el-input
              v-model="loginForm.username"
              placeholder="请输入用户名"
              size="large"
              class="custom-input"
              @keyup.enter="handleLogin"
            />
          </div>
        </el-form-item>

        <el-form-item prop="password">
          <div class="input-wrapper">
            <el-icon class="input-icon"><Lock /></el-icon>
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="请输入密码"
              size="large"
              class="custom-input"
              @keyup.enter="handleLogin"
            />
          </div>
        </el-form-item>

        <el-form-item prop="captchaCode">
          <div class="captcha-wrapper">
            <div class="input-wrapper captcha-input-wrapper">
              <el-icon class="input-icon"><Key /></el-icon>
              <el-input
                v-model="loginForm.captchaCode"
                placeholder="请输入验证码"
                size="large"
                class="custom-input"
                @keyup.enter="handleLogin"
              />
            </div>
            <div class="captcha-image" @click="refreshCaptcha">
              <img v-if="captchaImage" :src="captchaImage" alt="验证码" />
              <div v-else class="captcha-loading">加载中</div>
            </div>
          </div>
        </el-form-item>

        <el-form-item>
          <el-checkbox v-model="loginForm.rememberMe" class="remember-checkbox">记住我</el-checkbox>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="login-button"
            @click="handleLogin"
          >
            <span v-if="!loading">登 录</span>
            <span v-else>登录中...</span>
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-footer">
        <div class="divider">
          <span>Welcome</span>
        </div>
        <p class="footer-text">开始您的建模之旅</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Key } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/authStore'
import { authApi } from '@/api/authApi'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const formRef = ref()
const loading = ref(false)
const captchaImage = ref('')
const captchaKey = ref('')

const loginForm = reactive({
  username: '',
  password: '',
  captchaCode: '',
  rememberMe: false
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

const refreshCaptcha = async () => {
  try {
    const res = await authApi.getCaptcha()
    captchaKey.value = res.data.captchaKey
    captchaImage.value = res.data.captchaImage
    loginForm.captchaCode = ''
  } catch {
    ElMessage.error('获取验证码失败')
  }
}

const handleLogin = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  try {
    loading.value = true

    await authStore.login(
      loginForm.username,
      loginForm.password,
      captchaKey.value,
      loginForm.captchaCode,
      loginForm.rememberMe
    )
    ElMessage.success('登录成功')

    const redirect = (route.query.redirect as string) || '/models'
    router.push(redirect)
  } catch (error: any) {
    ElMessage.error(error.message || '登录失败，请检查用户名和密码')
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  refreshCaptcha()
})
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background: #f5f7fa;
}

.login-background {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  overflow: hidden;
}

.bg-decoration {
  position: absolute;
  border-radius: 50%;
  opacity: 0.4;
}

.bg-1 {
  width: 400px;
  height: 400px;
  background: #667eea;
  top: -150px;
  right: -100px;
  filter: blur(80px);
}

.bg-2 {
  width: 300px;
  height: 300px;
  background: #764ba2;
  bottom: -100px;
  left: -50px;
  filter: blur(60px);
}

.bg-3 {
  width: 200px;
  height: 200px;
  background: #667eea;
  top: 40%;
  left: 20%;
  filter: blur(40px);
  opacity: 0.2;
}

.login-card {
  width: 480px;
  padding: 48px 36px;
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  position: relative;
  z-index: 1;
  animation: cardAppear 0.6s ease-out;
}

@keyframes cardAppear {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.login-header {
  text-align: center;
  margin-bottom: 40px;
}

.logo-icon {
  width: 72px;
  height: 72px;
  margin: 0 auto 20px;
  background: #667eea;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.title {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
  margin: 0 0 8px;
}

.subtitle {
  font-size: 14px;
  color: #909399;
  margin: 0;
}

.login-form {
  margin-bottom: 32px;
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  width: 100%;
}

.input-icon {
  position: absolute;
  left: 16px;
  color: #909399;
  font-size: 18px;
  z-index: 1;
}

.custom-input {
  padding-left: 44px;
  width: 100%;
}

:deep(.custom-input) {
  width: 100%;
}

:deep(.custom-input .el-input__wrapper) {
  width: 100%;
  border-radius: 10px;
  padding: 12px 16px;
  box-shadow: 0 0 0 1px #dcdfe6 inset;
  transition: all 0.3s ease;
}

:deep(.el-form-item) {
  width: 100%;
}

:deep(.el-form-item__content) {
  width: 100%;
}

:deep(.custom-input .el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #909399 inset;
}

:deep(.custom-input .el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #667eea inset;
}

:deep(.custom-input .el-input__inner) {
  font-size: 15px;
}

.captcha-wrapper {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 12px;
}

.captcha-input-wrapper {
  flex: 1;
}

.captcha-image {
  width: 120px;
  height: 40px;
  flex-shrink: 0;
  cursor: pointer;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid #dcdfe6;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.captcha-image:hover {
  border-color: #667eea;
  box-shadow: 0 0 0 1px #667eea;
}

.captcha-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.captcha-loading {
  font-size: 12px;
  color: #909399;
}

.remember-checkbox {
  color: #606266;
}

:deep(.remember-checkbox .el-checkbox__input.is-checked .el-checkbox__inner) {
  background: #667eea;
  border-color: #667eea;
}

.login-button {
  width: 100%;
  height: 46px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 10px;
  margin-top: 8px;
  background: #667eea;
  border: none;
  transition: all 0.3s ease;
}

.login-button:hover {
  background: #764ba2;
}

.login-footer {
  text-align: center;
}

.divider {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: #e4e7ed;
}

.divider span {
  padding: 0 16px;
  color: #c0c4cc;
  font-size: 12px;
  letter-spacing: 2px;
}

.footer-text {
  margin: 0;
  color: #909399;
  font-size: 13px;
}
</style>
