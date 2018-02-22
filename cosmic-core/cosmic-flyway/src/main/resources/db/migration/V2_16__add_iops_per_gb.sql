-- Add miniopspergb, maxiopspergb, highestminiops, highestmaxiops
ALTER TABLE `disk_offering`
  ADD COLUMN `min_iops_per_gb` int unsigned, ADD COLUMN `max_iops_per_gb` int unsigned, ADD COLUMN `highest_min_iops` int unsigned, ADD COLUMN `highest_max_iops` int unsigned;

ALTER VIEW `disk_offering_view`
  ADD COLUMN `min_iops_per_gb` int unsigned, ADD COLUMN `max_iops_per_gb` int unsigned, ADD COLUMN `highest_min_iops` int unsigned, ADD COLUMN `highest_max_iops` int unsigned;
