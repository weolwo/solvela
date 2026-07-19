<!--
  * 首页 用户头部信息
  *
  * @Author:    1024创新实验室-主任：卓大
  * @Date:      2022-09-12 22:34:00
  * @Wechat:    zhuda1024
  * @Email:     lab1024@163.com
  * @Copyright  1024创新实验室 （ https://1024lab.net ），Since 2012
  *
-->
<template>
  <!-- 引入 Tailwind CSS v4 后，布局可以直接使用 mb-1.5 代替 margin-bottom: 5px -->
  <a-card class="w-full mb-1.5 p-0" :body-style="{ padding: 0 }">
    <a-page-header :title="welcomeSentence">
      <template #subTitle>
        <a-typography-text type="secondary" class="ml-5">所属部门： {{ departmentName }}</a-typography-text>
      </template>
      <a-row class="flex justify-between">
        <span class="w-[calc(100%-420px)]">
          <p class="m-0 mt-0.5 p-0 text-xs text-[#333] break-all"><AlertOutlined />{{ lastLoginInfo }}</p>
          <a class="block m-0 mt-1.5 pt-1 text-xs text-[#acacac] break-all hover:cursor-pointer hover:underline" href="#" target="_blank">
            <smile-outlined spin /> {{ heartSentence }}
          </a>
        </span>
      </a-row>
    </a-page-header>
  </a-card>
</template>

<script setup>
  import { computed } from 'vue';
  import { useUserStore } from '/@/store/modules/system/user';
  import uaparser from 'ua-parser-js';
  import _ from 'lodash';
  import heartSentenceArray from './heart-sentence';

  const userStore = useUserStore();

  const departmentName = computed(() => userStore.departmentName);

  // 欢迎语
  const welcomeSentence = computed(() => {
    let sentence = '';
    let now = new Date().getHours();
    if (now > 0 && now <= 6) {
      sentence = '午夜好，';
    } else if (now > 6 && now <= 11) {
      sentence = '早上好，';
    } else if (now > 11 && now <= 14) {
      sentence = '中午好，';
    } else if (now > 14 && now <= 18) {
      sentence = '下午好，';
    } else {
      sentence = '晚上好，';
    }
    return sentence + userStore.$state.actualName;
  });

  //上次登录信息
  const lastLoginInfo = computed(() => {
    let info = '';
    if (userStore.$state.lastLoginTime) {
      info = info + '上次登录:' + userStore.$state.lastLoginTime;
    }

    if (userStore.$state.lastLoginUserAgent) {
      let ua = uaparser(userStore.$state.lastLoginUserAgent);
      info = info + '; 设备:';
      if (ua.browser.name) {
        info = info + ' ' + ua.browser.name;
      }
      if (ua.os.name) {
        info = info + ' ' + ua.os.name;
      }
      let device = ua.device.vendor ? ua.device.vendor + ua.device.model : null;
      if (device) {
        info = info + ' ' + device + ';';
      }
    }

    if (userStore.$state.lastLoginIpRegion) {
      info = info + '; ' + userStore.$state.lastLoginIpRegion;
    }
    if (userStore.$state.lastLoginIp) {
      info = info + '; ' + userStore.$state.lastLoginIp;
    }
    return info;
  });

  // 毒鸡汤
  const heartSentence = computed(() => {
    return heartSentenceArray[_.random(0, heartSentenceArray.length - 1)];
  });
</script>
