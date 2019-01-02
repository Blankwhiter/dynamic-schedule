
package com.example.dynamicschedule.service.impl;

import com.example.dynamicschedule.bean.ScheduleJobLog;
import com.example.dynamicschedule.bean.ScheduleJobLogExample;
import com.example.dynamicschedule.dao.ScheduleJobLogMapper;
import com.example.dynamicschedule.service.ScheduleJobLogService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service("scheduleJobLogService")
public class ScheduleJobLogServiceImpl implements ScheduleJobLogService {


	@Autowired
	private ScheduleJobLogMapper scheduleJobLogMapper;

	@Override
	public PageInfo queryPage(Map<String, Object> params) {
		Long jobId = Long.parseLong(params.get("jobId").toString());
		Integer page = Integer.parseInt(params.get("page").toString());
		Integer pageSize = Integer.parseInt(params.get("pageSize").toString());

		PageHelper.startPage(page,pageSize);
		ScheduleJobLogExample scheduleJobLogExample = new ScheduleJobLogExample();
		scheduleJobLogExample.createCriteria().andJobIdEqualTo(jobId);
		List<ScheduleJobLog> scheduleJobLogs = scheduleJobLogMapper.selectByExample(scheduleJobLogExample);
		PageInfo pageInfo = new PageInfo<>(scheduleJobLogs);
		return pageInfo;
	}

}
