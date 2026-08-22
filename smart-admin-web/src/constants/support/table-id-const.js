/*
 * @Description: 表格id
 * @Author: zhuoda
 * @Date: 2022-08-21
 * @LastEditTime: 2022-08-21
 * @LastEditors: zhuoda
 */

//system系统功能表格初始化id
let systemInitTableId = 10000;

//support支撑功能表格初始化id
let supportInitTableId = 20000;

//业务表格初始化id
let businessOAInitTableId = 30000;

let businessERPInitTableId = 40000;

//营销中台（活动/抽奖/彩票/任务/奖品/风控/账务）表格初始化id
let businessMarketingInitTableId = 50000;

/**
 * 「列设置」里默认不勾选的列。
 *
 * 代码生成器产出的列表页会把表里每个字段都列出来，其中创建人、更新人、更新时间
 * 这类审计字段几乎没人看，却把真正要看的业务字段挤到屏幕外。
 *
 * ⚠️ 只是<b>默认</b>不显示，不是删除 —— 运营在「列设置」里随时能勾回来，且勾选结果按
 * 「每用户每表」持久化（后端 selectByUserIdAndTableId）。
 *
 * ⚠️ 刻意<b>不含 createTime</b>：创建时间是排查问题时最常用的一列，
 * 隐藏它的代价远大于省下的那点宽度。
 */
export const DEFAULT_HIDDEN_COLUMNS = [
  'createBy', //创建人
  'updateBy', //更新人
  'updateTime', //更新时间（创建时间保留）
  'deletedFlag', //删除标记
  'scriptId', //规则脚本id，运营看不懂也用不到
  'ext', //扩展 JSON，展开是一坨字符串
];

export const TABLE_ID_CONST = {
  /**
   * 业务
   */
  BUSINESS: {
    OA: {
      NOTICE: businessOAInitTableId + 1, //通知公告
      ENTERPRISE: businessOAInitTableId + 2, //企业信息
      ENTERPRISE_EMPLOYEE: businessOAInitTableId + 3, //企业员工
      ENTERPRISE_BANK: businessOAInitTableId + 4, //企业银行
      ENTERPRISE_INVOICE: businessOAInitTableId + 5, //企业发票
    },
    ERP: {
      GOODS: businessERPInitTableId + 1, //商品管理
    },
    /**
     * 营销中台。id 一经分配不要再改动 ——
     * 它是 t_table_column 里用户列设置的存储键，改了等于把所有人的设置作废。
     */
    MARKETING: {
      ACTIVITY_CONFIG: businessMarketingInitTableId + 1, //活动配置
      // 抽奖
      PRIZE_POOL_CONFIG: businessMarketingInitTableId + 11, //奖池配置
      PRIZE_POOL_ITEM: businessMarketingInitTableId + 12, //奖池物资
      POOL_PRIZE_MAPPING: businessMarketingInitTableId + 13, //奖池坑位映射
      // 彩票
      LOTTERY_CONFIG: businessMarketingInitTableId + 21, //彩票玩法
      LOTTERY_ISSUE: businessMarketingInitTableId + 22, //彩票期号
      LOTTERY_PRIZE_RULE: businessMarketingInitTableId + 23, //彩票奖级规则
      LOTTERY_RECORD: businessMarketingInitTableId + 24, //购彩记录
      // 任务
      TASK_CONFIG: businessMarketingInitTableId + 31, //任务配置
      TASK_RECORD: businessMarketingInitTableId + 32, //任务记录
      TASK_TEMPLATE: businessMarketingInitTableId + 33, //任务模板
      TASK_EVENT: businessMarketingInitTableId + 34, //任务事件注册表
      // 奖品
      PRIZE_CONFIG: businessMarketingInitTableId + 41, //奖品配置
      PRIZE_LOG: businessMarketingInitTableId + 42, //发奖记录
      TASK_PRIZE_MAPPING: businessMarketingInitTableId + 43, //任务奖励映射
      // 风控
      PROMOTION_CONFIG: businessMarketingInitTableId + 51, //优惠配置
      PROPOSAL_RECORD: businessMarketingInitTableId + 52, //提案记录
      // 账务
      MEMBER_WALLET: businessMarketingInitTableId + 61, //会员钱包
      MEMBER_COUPON: businessMarketingInitTableId + 62, //会员优惠券
      MEMBER_ASSET_TRANSACTION: businessMarketingInitTableId + 63, //资产流水
      PHYSICAL_DELIVERY: businessMarketingInitTableId + 64, //实物履约
    },
  },

  /**
   * 系统
   */
  SYSTEM: {
    EMPLOYEE: systemInitTableId + 1, //员工
    MENU: systemInitTableId + 2, //菜单
    POSITION: systemInitTableId + 3, //职位
  },
  /**
   * 支撑
   */
  SUPPORT: {
    CONFIG: supportInitTableId + 1, //参数配置
    DICT: supportInitTableId + 2, //字典
    SERIAL_NUMBER: supportInitTableId + 3, //单号
    OPERATE_LOG: supportInitTableId + 4, //请求监控
    HEART_BEAT: supportInitTableId + 5, //心跳
    LOGIN_LOG: supportInitTableId + 6, //登录日志
    RELOAD: supportInitTableId + 7, //reload
    HELP_DOC: supportInitTableId + 8, //帮助文档
    JOB: supportInitTableId + 9, //Job
    JOB_LOG: supportInitTableId + 10, //JobLog
    MAIL: supportInitTableId + 11,
    CHANGE_LOG: supportInitTableId + 12, //更新日志
    FILE: supportInitTableId + 13, //文件
    LOGIN_FAIL: supportInitTableId + 14, //登录失败
  },
};
