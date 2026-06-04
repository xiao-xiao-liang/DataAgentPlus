-- 地铁历史运行轨迹表语义模型初始化脚本
-- 执行前请将 @agent_id 与 @datasource_id 修改为实际值。

SET @agent_id = 0;
SET @datasource_id = 0;

INSERT INTO semantic_model
(agent_id, datasource_id, table_name, column_name, business_name, synonyms, business_description, column_comment, data_type, status)
VALUES
(@agent_id, @datasource_id, 'metro_history_trajectories', 'train_no', '列车车次号',
 '车次,车号,列车编号,列车号,train_no',
 '列车车次号或车号，用于按列车统计准点率、延误次数、平均延误时长和运行稳定性。',
 '列车车次号/车号', 'varchar(32)', 1),
(@agent_id, @datasource_id, 'metro_history_trajectories', 'from_station_id', '起始车站',
 '出发站,始发站,起点站,区间起点,from_station_id',
 '运行区间的起始车站 ID，可用于按出发站或区间起点分析准点率和延误情况。',
 '起始车站ID', 'varchar(16)', 1),
(@agent_id, @datasource_id, 'metro_history_trajectories', 'to_station_id', '到达车站',
 '终点站,目的站,到站,区间终点,to_station_id',
 '运行区间的到达车站 ID，可用于按到达站或区间终点分析准点率和延误情况。',
 '到达车站ID', 'varchar(16)', 1),
(@agent_id, @datasource_id, 'metro_history_trajectories', 'actual_start_time', '实际出发时间',
 '出发时间,进入区间时间,实际开始时间,运行开始时间,actual_start_time',
 '列车实际离开始发站或进入运行区间的时间，可用于按日期、小时、早晚高峰等时间维度统计运行表现。',
 '实际出发时间（离开始发站或进入区间时间）', 'timestamp with time zone', 1),
(@agent_id, @datasource_id, 'metro_history_trajectories', 'actual_end_time', '实际到达时间',
 '到达时间,离开区间时间,实际结束时间,运行结束时间,actual_end_time',
 '列车实际到达终点站或离开运行区间的时间，可与实际出发时间计算实际运行耗时。',
 '实际到达时间（到达终点站或离开区间时间）', 'timestamp with time zone', 1),
(@agent_id, @datasource_id, 'metro_history_trajectories', 'theoretical_duration_seconds', '理论运行耗时',
 '计划运行耗时,理论耗时,计划工时,理论工时,标准耗时,theoretical_duration_seconds',
 '区间理论或计划运行耗时，单位秒，可用于对比实际运行耗时并分析运行偏差。',
 '理论/计划运行工时（秒）', 'integer', 1),
(@agent_id, @datasource_id, 'metro_history_trajectories', 'delay_seconds', '延误时长',
 '晚点时长,延迟时长,延误秒数,晚点秒数,准点率,准时率,按时率,延误率,delay_seconds',
 '延误时长，单位秒。默认 delay_seconds <= 0 视为准点，delay_seconds > 0 视为延误。整体准点率 = 准点记录数 / 总运行记录数 * 100%。',
 '延误时长（秒，实际耗时与理论耗时的差值，或相较于时刻表的延误）', 'integer', 1),
(@agent_id, @datasource_id, 'metro_history_trajectories', 'created_at', '记录创建时间',
 '入库时间,创建时间,数据创建时间,created_at',
 '轨迹记录创建时间，可用于分析数据新鲜度、入库延迟和数据同步情况。',
 '记录创建时间', 'timestamp with time zone', 1);
