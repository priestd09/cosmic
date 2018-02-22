-- Add miniopspergb, maxiopspergb, highestminiops, highestmaxiops
ALTER TABLE `disk_offering`
  ADD COLUMN `min_iops_per_gb` int unsigned, ADD COLUMN `max_iops_per_gb` int unsigned, ADD COLUMN `highest_min_iops` int unsigned, ADD COLUMN `highest_max_iops` int unsigned;

DROP VIEW IF EXISTS `cloud`.`disk_offering_view`;
CREATE ALGORITHM=UNDEFINED DEFINER=`cloud`@`%` SQL SECURITY DEFINER VIEW `disk_offering_view`
AS SELECT
   `disk_offering`.`id` AS `id`,
   `disk_offering`.`uuid` AS `uuid`,
   `disk_offering`.`name` AS `name`,
   `disk_offering`.`display_text` AS `display_text`,
   `disk_offering`.`provisioning_type` AS `provisioning_type`,
   `disk_offering`.`disk_size` AS `disk_size`,
   `disk_offering`.`min_iops` AS `min_iops`,
   `disk_offering`.`max_iops` AS `max_iops`,
   `disk_offering`.`created` AS `created`,
   `disk_offering`.`tags` AS `tags`,
   `disk_offering`.`customized` AS `customized`,
   `disk_offering`.`customized_iops` AS `customized_iops`,
   `disk_offering`.`removed` AS `removed`,
   `disk_offering`.`use_local_storage` AS `use_local_storage`,
   `disk_offering`.`system_use` AS `system_use`,
   `disk_offering`.`hv_ss_reserve` AS `hv_ss_reserve`,
   `disk_offering`.`bytes_read_rate` AS `bytes_read_rate`,
   `disk_offering`.`bytes_write_rate` AS `bytes_write_rate`,
   `disk_offering`.`iops_read_rate` AS `iops_read_rate`,
   `disk_offering`.`iops_write_rate` AS `iops_write_rate`,
   `disk_offering`.`min_iops_per_gb` AS `min_iops_per_gb`,
   `disk_offering`.`max_iops_per_gb` AS `max_iops_per_gb`,
   `disk_offering`.`highest_min_iops` AS `highest_min_iops`,
   `disk_offering`.`highest_max_iops` AS `highest_max_iops`,
   `disk_offering`.`cache_mode` AS `cache_mode`,
   `disk_offering`.`sort_key` AS `sort_key`,
   `disk_offering`.`type` AS `type`,
   `disk_offering`.`display_offering` AS `display_offering`,
   `domain`.`id` AS `domain_id`,
   `domain`.`uuid` AS `domain_uuid`,
   `domain`.`name` AS `domain_name`,
   `domain`.`path` AS `domain_path`
FROM (`disk_offering` left join `domain` on((`disk_offering`.`domain_id` = `domain`.`id`))) where (`disk_offering`.`state` = 'ACTIVE');
