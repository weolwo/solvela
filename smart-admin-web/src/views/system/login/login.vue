<!--
  * 登录
  *
  * 2026-08-03 精简：删掉左侧整块宣传栏（1024lab 介绍 / 卓大微信 / 公众号跑马灯）、
  * 「9种登录背景风格」的推广通知与外链、右上角二维码，以及一堆从未渲染的图标 import。
  * 同时删掉了 login2 / login3 两套备用皮肤（零引用）和 18 张只被它们用到的图片。
  * 现在只剩一套登录页，登录相关逻辑（验证码开关 / 双因子邮箱验证码 / 密码 SM4 加密）一行没动。
  *
  * @Author:    1024创新实验室-主任：卓大
  * @Copyright  1024创新实验室 （ https://1024lab.net ），Since 2012
  *
-->
<template>
  <div class="login-container">
    <div class="box-item login">
      <div class="login-title">账号登录</div>
      <a-form ref="formRef" class="login-form" :model="loginForm" :rules="rules">
        <a-form-item name="loginName">
          <a-input v-model:value.trim="loginForm.loginName" placeholder="请输入用户名" />
        </a-form-item>
        <a-form-item name="emailCode" v-if="emailCodeShowFlag">
          <a-input-group compact>
            <a-input style="width: calc(100% - 110px)" v-model:value="loginForm.emailCode" autocomplete="on" placeholder="请输入邮箱验证码" />
            <a-button @click="sendSmsCode" class="code-btn" type="primary" :disabled="emailCodeButtonDisabled">
              {{ emailCodeTips }}
            </a-button>
          </a-input-group>
        </a-form-item>
        <a-form-item name="password">
          <a-input-password
            v-model:value="loginForm.password"
            autocomplete="on"
            :type="showPassword ? 'text' : 'password'"
            placeholder="请输入密码"
          />
        </a-form-item>
        <a-form-item>
          <div class="btn" @click="onLogin">登录</div>
        </a-form-item>
      </a-form>
    </div>
  </div>
</template>
<script setup>
  import { message } from 'ant-design-vue';
  import { onMounted, onUnmounted, reactive, ref } from 'vue';
  import { useRouter } from 'vue-router';
  import { loginApi } from '/@/api/system/login-api';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { LOGIN_DEVICE_ENUM } from '/@/constants/system/login-device-const';
  import { useUserStore } from '/@/store/modules/system/user';
  import { buildRoutes } from '/@/router/index';
  import { smartSentry } from '/@/lib/smart-sentry';
  import { encryptData } from '/@/lib/encrypt';
  import { localSave } from '/@/utils/local-util';
  import LocalStorageKeyConst from '/@/constants/local-storage-key-const';
  import { useDictStore } from '/@/store/modules/system/dict';
  import { dictApi } from '/@/api/support/dict-api';

  //--------------------- 登录表单 ---------------------------------
  const rules = {
    loginName: [{ required: true, message: '用户名不能为空' }],
    password: [{ required: true, message: '密码不能为空' }],
  };
  const loginForm = reactive({
    loginName: 'admin',
    password: '',
    loginDevice: LOGIN_DEVICE_ENUM.PC.value,
  });

  const showPassword = ref(false);
  const router = useRouter();
  const formRef = ref();

  onMounted(() => {
    document.onkeyup = (e) => {
      if (e.keyCode === 13) {
        onLogin();
      }
    };
  });

  onUnmounted(() => {
    document.onkeyup = null;
  });

  //登录
  async function onLogin() {
    formRef.value.validate().then(async () => {
      try {
        SmartLoading.show();
        // 密码加密
        let encryptPasswordForm = Object.assign({}, loginForm, {
          password: encryptData(loginForm.password),
        });
        const res = await loginApi.login(encryptPasswordForm);
        localSave(LocalStorageKeyConst.USER_TOKEN, res.data.token ? res.data.token : '');
        message.success('登录成功');
        //更新用户信息到pinia
        useUserStore().setUserLoginInfo(res.data);
        // 初始化数据字典
        const dictRes = await dictApi.getAllDictData();
        useDictStore().initData(dictRes.data);
        //构建系统的路由
        buildRoutes();
        router.push('/home');
      } catch (e) {
        smartSentry.captureError(e);
      } finally {
        SmartLoading.hide();
      }
    });
  }

  onMounted(() => {
    getTwoFactorLoginFlag();
  });

  //--------------------- 邮箱验证码 ---------------------------------

  const emailCodeShowFlag = ref(false);
  let emailCodeTips = ref('获取邮箱验证码');
  let emailCodeButtonDisabled = ref(false);
  // 定时器
  let countDownTimer = null;

  // 开始倒计时
  function runCountDown() {
    emailCodeButtonDisabled.value = true;
    let countDown = 60;
    emailCodeTips.value = `${countDown}秒后重新获取`;
    countDownTimer = setInterval(() => {
      if (countDown > 1) {
        countDown--;
        emailCodeTips.value = `${countDown}秒后重新获取`;
      } else {
        clearInterval(countDownTimer);
        emailCodeButtonDisabled.value = false;
        emailCodeTips.value = '获取验证码';
      }
    }, 1000);
  }

  // 获取双因子登录标识
  async function getTwoFactorLoginFlag() {
    try {
      let result = await loginApi.getTwoFactorLoginFlag();
      emailCodeShowFlag.value = result.data;
    } catch (e) {
      smartSentry.captureError(e);
    }
  }

  // 发送邮箱验证码
  async function sendSmsCode() {
    try {
      SmartLoading.show();
      let result = await loginApi.sendLoginEmailCode(loginForm.loginName);
      message.success('验证码发送成功!请登录邮箱查看验证码~');
      runCountDown();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }
</script>
<style lang="less" scoped>
  @import './login.less';
</style>
