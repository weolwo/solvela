-- =====================================================================================
-- 新设备登录提醒：两条模板（已上线的库执行一次；新库走 data-baseline.sql 已包含）
--
-- 🔴 缺了它们【不报错】：登录照常成功，只是提醒静默发不出去 —— 盗号者登进来，主人收不到任何告警。
--    上线后用一个老账号在新浏览器登录一次，确认邮箱和站内信都收到了。
--
-- INSERT IGNORE：重复执行无副作用；已存在同主键的行（被后台改过文案）不覆盖。
--
-- @date 2026-09-27
-- =====================================================================================

-- 站内信（SYSTEM 类，用户关不掉）
INSERT IGNORE INTO `t_notification_template` (`template_code`, `version`, `category`, `title_template`, `content_template`, `param_keys`, `status`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('NEW_DEVICE_LOGIN', 1, 'SYSTEM', '新设备登录提醒', '你的账号于 ${loginTime} 在一台新设备（${deviceType}，${location}）上登录。如果不是你本人操作，请立即修改密码，并在「我的登录设备」中下线其他设备。', '["loginTime", "deviceType", "location"]', 1, 'system', '2026-09-27 00:26:16', NULL, '2026-09-27 00:26:16');

-- 邮件（正文不带任何链接：钓鱼邮件最爱仿「点此处理」，真提醒一律不放链接）
INSERT IGNORE INTO `t_mail_template` (`template_code`, `template_subject`, `template_content`, `template_type`, `disable_flag`, `update_time`, `create_time`) VALUES
('member_new_device_login', '新设备登录提醒', '<!DOCTYPE HTML><html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/></head><body>\n<div style="margin:0 auto;width:690px;font-family:Helvetica,Arial,sans-serif;line-height:28px;">\n  <h2>新设备登录提醒</h2>\n  <p>你的账号刚刚在一台<b>新设备</b>上登录：</p>\n  <p>时间：${loginTime}<br/>设备：${deviceType}<br/>地点：${location}</p>\n  <p>如果是你本人，无需任何操作。</p>\n  <p style="color:#b00;"><b>如果不是你本人</b>，说明有人知道了你的密码。请立即打开 App 修改密码，并在「我的登录设备」中点「下线其他设备」。</p>\n  <p style="color:#888;font-size:13px;">这封信里没有任何链接。收到带链接、要你点击登录或验证的「安全提醒」，那是钓鱼邮件。</p>\n</div></body></html>', 'freemarker', 0, '2026-09-27 00:26:16', '2026-09-27 00:26:16');
