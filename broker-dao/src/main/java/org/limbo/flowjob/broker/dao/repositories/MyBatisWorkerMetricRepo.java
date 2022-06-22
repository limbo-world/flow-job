/*
 * Copyright 2020-2024 Limbo Team (https://github.com/limbo-world).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.limbo.flowjob.broker.dao.repositories;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.common.collect.Lists;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.limbo.flowjob.broker.core.worker.metric.WorkerExecutor;
import org.limbo.flowjob.broker.core.worker.metric.WorkerMetric;
import org.limbo.flowjob.broker.core.worker.metric.WorkerMetricRepository;
import org.limbo.flowjob.broker.dao.converter.WorkerExecutorPoConverter;
import org.limbo.flowjob.broker.dao.converter.WorkerMetricPoConverter;
import org.limbo.flowjob.broker.dao.entity.WorkerExecutorEntity;
import org.limbo.flowjob.broker.dao.entity.WorkerMetricEntity;
import org.limbo.flowjob.broker.dao.mybatis.WorkerExecutorMapper;
import org.limbo.flowjob.broker.dao.mybatis.WorkerMetricMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Brozen
 * @since 2021-06-03
 */
@Repository
public class MyBatisWorkerMetricRepo implements WorkerMetricRepository {

    @Autowired
    private WorkerMetricMapper mapper;

    @Autowired
    private WorkerMetricPoConverter converter;

    @Autowired
    private WorkerExecutorMapper workerExecutorMapper;

    /**
     * {@inheritDoc}
     *
     * @param metric worker指标信息
     */
    @Override
    public void updateMetric(WorkerMetric metric) {
        WorkerMetricEntity po = converter.convert(metric);
        Objects.requireNonNull(po);

        // 新增或插入worker指标
        int effected = mapper.update(po, Wrappers.<WorkerMetricEntity>lambdaUpdate()
                .eq(WorkerMetricEntity::getWorkerId, po.getWorkerId()));
        if (effected <= 0) {

            effected = mapper.insertIgnore(po);
            if (effected != 1) {
                throw new IllegalStateException(String.format("Update worker error, effected %s rows", effected));
            }
        }

        // 更新worker执行器
        workerExecutorMapper.delete(Wrappers.<WorkerExecutorEntity>lambdaQuery()
                .eq(WorkerExecutorEntity::getWorkerId, metric.getWorkerId()));
        List<WorkerExecutor> executors = metric.getExecutors();
        if (CollectionUtils.isNotEmpty(executors)) {
            workerExecutorMapper.batchInsert(executors.stream()
                    .map(workerExecutor -> {
                        if (StringUtils.isBlank(workerExecutor.getDescription())) {
                            workerExecutor.setDescription(StringUtils.EMPTY);
                        }
                        return WorkerExecutorPoConverter.INSTANCE.toEntity(workerExecutor);
                    })
                    .collect(Collectors.toList()));
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param workerId workerId
     * @return
     */
    @Override
    public WorkerMetric getMetric(String workerId) {
        // 查询metric
        WorkerMetricEntity metricPo = mapper.selectById(workerId);
        WorkerMetric metric = converter.reverse().convert(metricPo);
        if (metric == null) {
            return null;
        }

        // 查询执行器
        List<WorkerExecutor> executors;
        List<WorkerExecutorEntity> executorPos = workerExecutorMapper.findByWorker(workerId);
        if (CollectionUtils.isNotEmpty(executorPos)) {
            executors = executorPos.stream()
                    .map(WorkerExecutorPoConverter.INSTANCE::toDO)
                    .collect(Collectors.toList());
        } else {
            executors = Lists.newArrayList();
        }
        metric.setExecutors(executors);

        return metric;
    }

}