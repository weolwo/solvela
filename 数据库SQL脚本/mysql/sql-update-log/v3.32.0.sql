-- 会员钱包表改造：从「一行多币种字段」改为「一行一种资产(asset_type + balance)」
-- 将来新增资产类型只需新增 asset_type 取值（对齐 PrizeTypeEnum，与 t_member_asset_transaction.asset_type 同一字典），无需加字段

-- 1. 新增列（asset_type 先给默认值 SCORE，便于存量数据迁移）
ALTER TABLE `t_member_wallet`
    ADD COLUMN `asset_type` varchar(32)    NOT NULL DEFAULT 'SCORE' COMMENT '资产类型：SCORE-积分, BALANCE-现金' AFTER `member_name`,
    ADD COLUMN `balance`    decimal(18, 4) NOT NULL DEFAULT '0.0000' COMMENT '余额' AFTER `asset_type`;

-- 2. 存量行迁移为积分资产行
UPDATE `t_member_wallet`
SET `asset_type` = 'SCORE',
    `balance`    = IFNULL(`score_balance`, 0);

-- 3. 调整唯一键：member_name -> (member_name, asset_type)，必须先于插入现金行
ALTER TABLE `t_member_wallet`
    DROP INDEX `uk_t_biz_mbr_mbr`,
    ADD UNIQUE KEY `uk_member_asset` (`member_name`, `asset_type`);

-- 4. 为存量会员补插现金资产行
INSERT INTO `t_member_wallet` (`tenant_id`, `member_name`, `asset_type`, `balance`, `status`, `version`, `create_by`)
SELECT `tenant_id`, `member_name`, 'BALANCE', IFNULL(`cash_balance`, 0), `status`, 0, `create_by`
FROM `t_member_wallet`
WHERE `asset_type` = 'SCORE';

-- 5. 删除旧的多币种字段，并去掉 asset_type 的临时默认值
ALTER TABLE `t_member_wallet`
    DROP COLUMN `score_balance`,
    DROP COLUMN `cash_balance`,
    ALTER COLUMN `asset_type` DROP DEFAULT;
