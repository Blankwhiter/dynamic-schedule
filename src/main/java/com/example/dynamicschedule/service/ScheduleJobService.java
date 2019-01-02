/**
 * Copyright 2018 人人开源 http://www.renren.io
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.example.dynamicschedule.service;

import com.example.dynamicschedule.bean.ScheduleJob;
import com.github.pagehelper.PageInfo;

import java.util.List;
import java.util.Map;

/**
 * 定时任务
 *
 */
public interface ScheduleJobService   {

	PageInfo queryPage(Map<String, Object> params);

	/**
	 * 保存定时任务
	 */
	void save(ScheduleJob scheduleJob);

	/**
	 * 更新定时任务
	 */
	void update(ScheduleJob scheduleJob);

	/**
	 * 批量删除定时任务
	 */
	void deleteBatch(List<Long> jobIds);

	/**
	 * 批量更新定时任务状态
	 */
	int updateBatch(List<Long> jobIds, int status);

	/**
	 * 立即执行
	 */
	void run(List<Long> jobIds);

	/**
	 * 暂停运行
	 */
	void pause(List<Long> jobIds);

	/**
	 * 恢复运行
	 */
	void resume(List<Long> jobIds);
}
