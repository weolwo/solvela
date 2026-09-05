<script setup lang="ts">
/**
 * 中秋专题页的节日底：纯装饰，零 props，对读屏隐藏。
 *
 * <h3>为什么是自绘 SVG，不是一张运营图</h3>
 * 后端给的是 {@code bgImageId}（file_id）不是 URL，而网关还没暴露文件下载接口
 *（头像、轮播图卡在同一个原因）。等文件接口通了，这整个组件换成一个 {@code <img>}，
 * ActivityView 一行不用动 —— 这也是它值得单独成文件的原因：
 * 它是<b>将来会被整体替换掉的那一块</b>。
 *
 * <p>放在 views/activity/ 而不是 ui/：它认识「中秋」，不是平台组件。
 * ui/ 下的东西必须对业务一无所知。
 */
</script>

<template>
  <svg
    class="backdrop"
    viewBox="0 0 390 844"
    preserveAspectRatio="xMidYMid slice"
    aria-hidden="true"
    xmlns="http://www.w3.org/2000/svg"
  >
    <defs>
      <radialGradient id="sky" cx="50%" cy="22%" r="90%">
        <stop offset="0%" stop-color="#3a5c86" />
        <stop offset="48%" stop-color="#264576" />
        <stop offset="100%" stop-color="#152a4c" />
      </radialGradient>
      <radialGradient id="moonGlow" cx="50%" cy="50%" r="50%">
        <stop offset="0%" stop-color="#fff6db" stop-opacity="0.95" />
        <stop offset="42%" stop-color="#ffe8ac" stop-opacity="0.34" />
        <stop offset="100%" stop-color="#ffe8ac" stop-opacity="0" />
      </radialGradient>
      <!-- 转盘背后的暖光，把整页从「冷蓝」拉到「暖夜」 -->
      <radialGradient id="warmGlow" cx="50%" cy="50%" r="50%">
        <stop offset="0%" stop-color="#ffb355" stop-opacity="0.4" />
        <stop offset="60%" stop-color="#ff9d3d" stop-opacity="0.12" />
        <stop offset="100%" stop-color="#ff9d3d" stop-opacity="0" />
      </radialGradient>
    </defs>

    <rect width="390" height="844" fill="url(#sky)" />
    <ellipse cx="195" cy="500" rx="340" ry="320" fill="url(#warmGlow)" />

    <!-- 中秋是圆月，不做月牙。塞在右上角，避开居中的标题。淡淡几块暗斑当环形山 -->
    <circle cx="342" cy="88" r="104" fill="url(#moonGlow)" />
    <circle cx="342" cy="88" r="46" fill="#fff4d2" />
    <circle cx="330" cy="76" r="8" fill="#f2e2b6" opacity="0.5" />
    <circle cx="353" cy="100" r="5.5" fill="#f2e2b6" opacity="0.45" />
    <circle cx="350" cy="74" r="3.5" fill="#f2e2b6" opacity="0.4" />

    <!-- 星点 -->
    <g fill="#fff6e6">
      <circle cx="40" cy="70" r="1.6" opacity="0.9" />
      <circle cx="96" cy="132" r="1.2" opacity="0.6" />
      <circle cx="150" cy="52" r="1.4" opacity="0.8" />
      <circle cx="70" cy="220" r="1.3" opacity="0.5" />
      <circle cx="336" cy="320" r="1.5" opacity="0.7" />
      <circle cx="360" cy="250" r="1.2" opacity="0.55" />
      <circle cx="300" cy="360" r="1.2" opacity="0.5" />
      <circle cx="24" cy="330" r="1.4" opacity="0.6" />
      <circle cx="40" cy="470" r="1.3" opacity="0.5" />
    </g>

    <!-- 云 -->
    <g fill="#2c4c7e" opacity="0.5">
      <ellipse cx="110" cy="210" rx="76" ry="17" />
      <ellipse cx="330" cy="270" rx="60" ry="14" />
    </g>

    <!-- 灯笼：每盏后面一团暖光 -->
    <g fill="#ffb454" opacity="0.5">
      <ellipse cx="58" cy="78" rx="30" ry="34" />
      <ellipse cx="330" cy="120" rx="24" ry="28" />
      <ellipse cx="150" cy="40" rx="18" ry="22" />
    </g>
    <g stroke="#f6c453" stroke-width="1.2">
      <line x1="58" y1="0" x2="58" y2="60" />
      <line x1="330" y1="0" x2="330" y2="96" />
      <line x1="150" y1="0" x2="150" y2="24" />
    </g>
    <g>
      <ellipse cx="58" cy="78" rx="18" ry="21" fill="#e23b2e" />
      <rect x="50" y="55" width="16" height="5" rx="2" fill="#f6c453" />
      <rect x="50" y="95" width="16" height="5" rx="2" fill="#f6c453" />
      <line x1="58" y1="100" x2="58" y2="112" stroke="#f6c453" stroke-width="1.4" />
      <ellipse cx="330" cy="120" rx="15" ry="18" fill="#e8552f" />
      <rect x="323" y="100" width="14" height="4" rx="2" fill="#f6c453" />
      <rect x="323" y="136" width="14" height="4" rx="2" fill="#f6c453" />
      <ellipse cx="150" cy="40" rx="11" ry="13" fill="#e23b2e" />
      <rect x="144" y="26" width="12" height="4" rx="2" fill="#f6c453" />
    </g>

    <!-- 桂花：几簇小碎点 -->
    <g fill="#ffd873">
      <g opacity="0.85">
        <circle cx="18" cy="180" r="2" />
        <circle cx="24" cy="186" r="1.8" />
        <circle cx="14" cy="190" r="1.6" />
        <circle cx="26" cy="176" r="1.5" />
      </g>
      <g opacity="0.8">
        <circle cx="374" cy="420" r="2" />
        <circle cx="368" cy="426" r="1.8" />
        <circle cx="378" cy="430" r="1.6" />
        <circle cx="366" cy="414" r="1.5" />
      </g>
    </g>

    <!-- 底部祥云带 -->
    <path
      d="M0 792c46 0 46-26 92-26s46 26 92 26 46-26 92-26 46 26 92 26 46-22 92-26v98H0z"
      fill="#122a4c"
    />
    <path
      d="M0 792c46 0 46-26 92-26s46 26 92 26 46-26 92-26 46 26 92 26 46-22 92-26"
      fill="none"
      stroke="#f2ce7e"
      stroke-opacity="0.55"
      stroke-width="1.4"
    />

    <rect
      x="9"
      y="9"
      width="372"
      height="826"
      rx="18"
      fill="none"
      stroke="#f2ce7e"
      stroke-opacity="0.32"
      stroke-width="1.5"
    />
  </svg>
</template>

<style scoped>
.backdrop {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}
</style>
