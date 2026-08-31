<!--
  * 脚本编辑器：写一个新版本
  *
  * 🔴 保存 ≠ 生效。这里只是往 t_script 加一行，线上跑的还是原来激活的那版，
  *    要生效得回列表里点「激活」。合成一步的话，「我先存一下待会儿再看」
  *    会直接改掉生产逻辑。
  *
  * 🔴 已有脚本改版时，编码与场景是锁死的：换场景 = 换掉入参与返回值契约，
  *    而挂载点是按场景校验后才挂上去的，悄悄换掉等于让已有挂载全部失效。
  *    后端也会拒，这里锁住只是不让人白填一遍。
  *
  * @Date 2026-08-31
-->
<template>
  <a-modal v-model:open="visible" :title="isNewScript ? '新增脚本' : `改一版：${form.scriptCode}`" width="1000px" :destroy-on-close="true" @ok="save">
    <a-form :model="form" :label-col="{ width: 96 }" layout="vertical">
      <a-row :gutter="16">
        <a-col :span="8">
          <a-form-item label="脚本编码" required>
            <a-input-group compact>
              <a-input v-model:value="form.scriptCode" :disabled="!isNewScript" style="width: calc(100% - 64px)" placeholder="如 activity/mid_autumn_play" />
              <a-button :disabled="!isNewScript" :loading="generating" @click="generateCode">生成</a-button>
            </a-input-group>
            <div class="field-hint">只能用字母、数字、下划线、中划线和斜杠。手写一个有意义的名字，日志里更好认</div>
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="脚本名称" required>
            <a-input v-model:value="form.scriptName" placeholder="给人看的名字" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="场景" required>
            <a-select v-model:value="form.scene" :disabled="!isNewScript" :options="sceneOptions" placeholder="决定入参与返回值契约" />
            <div v-if="!isNewScript" class="field-hint">已有版本的场景不能改，要换请用新的脚本编码</div>
          </a-form-item>
        </a-col>
      </a-row>

      <a-form-item label="用途说明">
        <a-input v-model:value="form.description" placeholder="这个脚本是干什么的" />
      </a-form-item>

      <!--
        🔴 选了一个没有已接入挂载点的场景，这里必须说清楚：
        脚本能写、能存、能激活，但挂载那一步会发现无处可挂 —— 而场景改不了。
      -->
      <a-alert v-if="sceneUnmountable" type="error" show-icon class="mb-3">
        <template #message>这个场景目前没有已接入的挂载点</template>
        <template #description>
          脚本可以写、可以保存，但<b>挂不上任何业务对象</b>：引擎里还没有代码会执行这个场景的槽位。
          而<b>场景创建后不能改</b>，选错就只能换一个脚本编码重建。<br />
          如果你要写的是「一次抽奖走哪条玩法分支、进哪个奖池」，请选<b>活动玩法编排</b>。
        </template>
      </a-alert>

      <!-- 场景变量放在编辑器上方：写脚本时最需要的就是「我能拿到什么」 -->
      <a-alert v-if="currentScene" type="info" class="mb-3">
        <template #message>
          本场景必须返回 <b>{{ currentScene.returnType }}</b
          >，可用变量：
          <a-tag v-for="param in currentScene.params" :key="param.name" :color="param.required ? 'red' : 'default'">
            {{ param.name }}
          </a-tag>
        </template>
        <template #description>
          <div class="scene-desc">{{ currentScene.description }}</div>
          <div v-for="param in currentScene.params" :key="param.name" class="param-line">
            <code>{{ param.name }}</code>
            <span class="param-type">{{ param.type }}</span>
            <span>{{ param.description }}</span>
          </div>
        </template>
      </a-alert>

      <a-form-item label="脚本内容" required>
        <SolvelaCodeEditor v-model:value="form.content" language="java" theme="vs-dark" height="320px" />
      </a-form-item>

      <a-form-item label="这一版改了什么">
        <a-input v-model:value="form.changeLog" placeholder="本地 git 记得住演化过程，这里记的是「为什么发这一版」" />
      </a-form-item>

      <a-alert v-if="checkResult" :type="checkResult.pass ? 'success' : 'error'" :message="checkResult.message" show-icon />
    </a-form>

    <template #footer>
      <div class="footer-bar">
        <span class="footer-hint">保存只是加一个版本，<b>不会改变线上行为</b> —— 要生效请回列表点「激活」</span>
        <a-space>
          <a-button :loading="checking" @click="checkSyntax">语法校验</a-button>
          <a-button @click="visible = false">取消</a-button>
          <a-button type="primary" :loading="saving" @click="save">保存为新版本</a-button>
        </a-space>
      </div>
    </template>
  </a-modal>
</template>

<script setup>
  import { computed, reactive, ref } from 'vue';
  import { message } from 'ant-design-vue';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import { scriptApi } from '/@/api/support/script-api.js';
  import SolvelaCodeEditor from '/@/components/business/code-editor/SolvelaCodeEditor.vue';

  const props = defineProps({
    scenes: { type: Array, default: () => [] },
  });

  const emit = defineEmits(['saved']);

  const visible = ref(false);
  const saving = ref(false);
  const checking = ref(false);
  const generating = ref(false);
  const checkResult = ref(null);
  const isNewScript = ref(true);

  const form = reactive({
    scriptCode: '',
    scriptName: '',
    scene: undefined,
    description: '',
    content: '',
    changeLog: '',
  });

  /*
   * 🔴 挂不上的场景标出来但<b>不禁选</b>。
   *
   * 禁掉会挡住「调用点还没写完，先把脚本准备好」这种正当用法；
   * 而完全不提示的代价更大 —— 场景创建后不可改，选错了整个脚本作废、只能换编码重建。
   * 所以：可选、但把后果摆在眼前。
   */
  const sceneOptions = computed(() =>
    props.scenes.map((scene) => ({
      value: scene.scene,
      label: scene.mountable === false ? `${scene.title}（挂载点未接入）` : `${scene.title}（${scene.scene}）`,
    }))
  );

  const sceneUnmountable = computed(() => currentScene.value?.mountable === false);

  const currentScene = computed(() => props.scenes.find((scene) => scene.scene === form.scene));

  /**
   * @param source 传 null 是新建；传一个版本对象是「基于它改一版」
   */
  function open(source) {
    checkResult.value = null;
    isNewScript.value = !source;
    Object.assign(form, {
      scriptCode: source?.scriptCode || '',
      scriptName: source?.scriptName || '',
      scene: source?.scene,
      description: source?.description || '',
      content: source?.content || '',
      // 改动说明不带过来：上一版的理由不是这一版的理由
      changeLog: '',
    });
    visible.value = true;
  }

  async function generateCode() {
    try {
      generating.value = true;
      // 与活动/奖池/奖品同一套编码约定，运营在哪个页面点「生成」拿到的形状都一样
      form.scriptCode = await scriptApi.generateCode();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      generating.value = false;
    }
  }

  async function checkSyntax() {
    if (!form.content.trim()) {
      message.warning('先写点内容');
      return;
    }
    try {
      checking.value = true;
      const res = await scriptApi.check(form.content);
      checkResult.value = res?.valid ? { pass: true, message: '语法通过' } : { pass: false, message: res?.detail || '语法有误' };
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      checking.value = false;
    }
  }

  async function save() {
    if (!form.scriptCode.trim() || !form.scriptName.trim() || !form.scene || !form.content.trim()) {
      message.warning('编码、名称、场景、内容都是必填的');
      return;
    }
    try {
      saving.value = true;
      await scriptApi.save({
        scriptCode: form.scriptCode.trim(),
        scriptName: form.scriptName.trim(),
        scene: form.scene,
        description: form.description,
        content: form.content,
        changeLog: form.changeLog,
      });
      // 措辞刻意区分两种结果：新建的首版是直接生效的，改版的不是
      message.success(isNewScript.value ? '已保存并激活（首版）' : '已保存为新版本，尚未生效 —— 请在版本列表里激活');
      visible.value = false;
      emit('saved');
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      saving.value = false;
    }
  }

  defineExpose({ open });
</script>

<style scoped lang="less">
  .field-hint {
    color: rgba(0, 0, 0, 0.45);
    font-size: 12px;
    margin-top: 2px;
  }

  .scene-desc {
    margin-bottom: 6px;
  }

  .param-line {
    font-size: 12px;
    line-height: 20px;

    code {
      margin-right: 6px;
    }
  }

  .param-type {
    color: #1677ff;
    margin-right: 8px;
  }

  .footer-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .footer-hint {
    color: rgba(0, 0, 0, 0.45);
    font-size: 12px;
  }

  .mb-3 {
    margin-bottom: 12px;
  }
</style>
