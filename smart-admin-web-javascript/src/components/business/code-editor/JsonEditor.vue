<!--
  * 系统设置 列表
  *
  * @Author:    1024创新实验室-主任：卓大
  * @Date:      2022-06-08 21:50:41
  * @Wechat:    zhuda1024
  * @Email:     lab1024@163.com
  * @Copyright  1024创新实验室 （ https://1024lab.net ），Since 2012
-->
<template>
  <div>
    <SmartRuleEditor
      v-model="currentScript"
      :original-value="dbScript"
      cache-key="freight_rule_draft"
      :dictionary="myApiDictionary"
      default-height="80vh"
      @submit="handleSaveToServer"
    />
  </div>
</template>
<script setup>
  import { onMounted, ref } from 'vue';
  import SmartRuleEditor from './SmartRuleEditor.vue';
  // 1. 假设这是从后端数据库查出来的【线上生效代码】
  const dbScript = ref('如果 (订单.金额 大于 100) 则 {\n  返回 计算运费(0);\n}');

  // 2. 当前正在编辑的代码（双向绑定）
  const currentScript = ref(dbScript.value);

  // 3. 从后端接口拉取的业务字典 (只传你们模块需要的字典)
  const myApiDictionary = ref({
    objects: {
      订单: {
        desc: '交易订单',
        properties: { 金额: { type: 'number', desc: '实付总额' } },
      },
    },
    functions: [{ label: '计算运费', insertText: '计算运费(${1:金额})', desc: '运费计算器' }],
    keywords: ['如果', '则', '否则', '返回', '大于'],
  });

  // 4. 用户在弹窗里点击【确认提交】后触发的业务逻辑
  const handleSaveToServer = async (newCode) => {
    console.log('即将发送给后端的最新规则：', newCode);

    // TODO: 发起 axios 请求保存到数据库
    // await axios.post('/api/rules/update', { script: newCode });

    // 成功后，更新原始基准代码，防止下次点提交依然触发 Diff
    dbScript.value = newCode;
    alert('🎉 规则已成功更新至服务器！');
  };
</script>
