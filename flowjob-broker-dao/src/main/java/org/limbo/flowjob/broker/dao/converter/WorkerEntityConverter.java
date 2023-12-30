/*
 * Copyright 2020-2024 Limbo Team (https://github.com/limbo-world).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.limbo.flowjob.broker.dao.converter;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.limbo.flowjob.api.constants.WorkerStatus;
import org.limbo.flowjob.broker.core.meta.IDGenerator;
import org.limbo.flowjob.broker.core.meta.IDType;
import org.limbo.flowjob.broker.core.worker.Worker;
import org.limbo.flowjob.broker.core.worker.executor.WorkerExecutor;
import org.limbo.flowjob.broker.core.worker.metric.WorkerAvailableResource;
import org.limbo.flowjob.broker.core.worker.metric.WorkerMetric;
import org.limbo.flowjob.broker.dao.entity.WorkerEntity;
import org.limbo.flowjob.broker.dao.entity.WorkerExecutorEntity;
import org.limbo.flowjob.broker.dao.entity.WorkerMetricEntity;
import org.limbo.flowjob.broker.dao.entity.WorkerTagEntity;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Brozen
 * @since 2022-09-22
 */
public class WorkerEntityConverter {

    /**
     * 将持久化对象{@link WorkerEntity}转换为领域对象{@link Worker}
     *
     * @param po {@link WorkerEntity}持久化对象
     * @return {@link Worker}领域对象
     */
    public static Worker toWorker(WorkerEntity po, List<WorkerExecutorEntity> executors, List<WorkerTagEntity> tags,
                                  WorkerMetricEntity metric) {
        // 已删除则不返回
        if (po == null || po.isDeleted()) {
            return null;
        }

        return Worker.builder()
                .id(po.getWorkerId())
                .name(po.getName())
                .url(workerRpcBaseUrl(po))
                .brokerUrl(DomainConverter.brokerUrl(po.getBrokerUrl()))
                .status(WorkerStatus.parse(po.getStatus()))
                .enabled(po.isEnabled())
                .tags(toTags(tags))
                .executors(toExecutors(executors))
                .metric(toMetric(metric))
                .build();
    }


    /**
     * 解析 worker 通信 URL
     */
    public static URL workerRpcBaseUrl(WorkerEntity po) {
        try {
            return new URL(po.getProtocol(), po.getHost(), po.getPort(), "");
        } catch (Exception e) {
            throw new IllegalStateException("parse worker rpc info error", e);
        }
    }

    /**
     * 将领域对象{@link Worker}转换为持久化对象{@link WorkerEntity}
     *
     * @param worker {@link Worker}领域对象
     * @return {@link WorkerEntity}持久化对象
     */
    public static WorkerEntity toWorkerEntity(Worker worker) {
        WorkerEntity po = new WorkerEntity();
        po.setWorkerId(worker.getId());
        po.setName(worker.getName());
        po.setProtocol(worker.getUrl().getProtocol());
        po.setHost(worker.getUrl().getHost());
        po.setPort(worker.getUrl().getPort());
        po.setBrokerUrl(worker.getBrokerUrl().toString());
        po.setStatus(worker.getStatus().status);
        po.setAppId("");
        po.setEnabled(true);
        po.setDeleted(false);
        return po;
    }

    /**
     * 将{@link WorkerMetricEntity}持久化对象转换为{@link WorkerMetric}值对象
     *
     * @param metric {@link WorkerMetricEntity}持久化对象
     * @return {@link WorkerMetric}值对象
     */
    public static WorkerMetric toMetric(WorkerMetricEntity metric) {
        return WorkerMetric.builder()
                .availableResource(new WorkerAvailableResource(
                        metric.getAvailableCpu(), metric.getAvailableRam(), metric.getAvailableQueueLimit()
                ))
                .lastHeartbeatAt(metric.getLastHeartbeatAt())
                .build();
    }


    /**
     * 将{@link WorkerMetric}值对象转换为{@link WorkerMetricEntity}持久化对象
     *
     * @param vo {@link WorkerMetric}值对象
     * @return {@link WorkerMetricEntity}持久化对象
     */
    public static WorkerMetricEntity toMetricEntity(String workerId, WorkerMetric vo) {
        WorkerMetricEntity po = new WorkerMetricEntity();
        po.setWorkerId(workerId);

        WorkerAvailableResource availableResource = vo.getAvailableResource();
        po.setAvailableCpu(availableResource.getAvailableCpu());
        po.setAvailableRam(availableResource.getAvailableRam());
        po.setAvailableQueueLimit(availableResource.getAvailableQueueLimit());

        po.setLastHeartbeatAt(vo.getLastHeartbeatAt());

        return po;
    }


    /**
     * 将执行器持久化对象转为领域模型
     */
    public static List<WorkerExecutor> toExecutors(List<WorkerExecutorEntity> entities) {
        if (CollectionUtils.isEmpty(entities)) {
            return Lists.newArrayList();
        }

        return entities.stream()
                .map(entity -> WorkerExecutor.builder()
                        .name(entity.getName())
                        .description(entity.getDescription())
                        .build())
                .collect(Collectors.toList());
    }


    /**
     * 提取 Worker 中的 executors，转为持久化对象列表
     */
    public static List<WorkerExecutorEntity> toExecutorEntities(String workerId, Worker worker, IDGenerator idGenerator) {
        List<WorkerExecutor> executors = worker.getExecutors();
        if (CollectionUtils.isEmpty(executors)) {
            return Lists.newArrayList();
        }

        return executors.stream()
                .map(exe -> {
                    WorkerExecutorEntity executor = new WorkerExecutorEntity();
                    executor.setWorkerExecutorId(idGenerator.generateId(IDType.WORKER_EXECUTOR));
                    executor.setWorkerId(workerId);
                    executor.setName(exe.getName());
                    executor.setDescription(exe.getDescription());
                    return executor;
                })
                .collect(Collectors.toList());
    }


    /**
     * 提取 Worker 中的 tags，转为持久化对象列表
     */
    public static Map<String, List<String>> toTags(List<WorkerTagEntity> tags) {
        if (CollectionUtils.isEmpty(tags)) {
            return Maps.newHashMap();
        }

        Map<String, List<String>> tagsMap = new HashMap<>();
        for (WorkerTagEntity tag : tags) {
            List<String> values = tagsMap.computeIfAbsent(tag.getTagKey(), k -> new ArrayList<>());
            values.add(tag.getTagValue());
        }

        return tagsMap;
    }


    /**
     * 提取 Worker 中的 tags，转为持久化对象列表
     */
    public static List<WorkerTagEntity> toTagEntities(String workerId, Worker worker, IDGenerator idGenerator) {
        Map<String, List<String>> tags = worker.getTags();
        if (MapUtils.isEmpty(tags)) {
            return Lists.newArrayList();
        }

        return tags.entrySet().stream()
                .flatMap(entry -> {
                    List<String> values = entry.getValue();
                    if (CollectionUtils.isEmpty(values)) {
                        return Stream.empty();
                    }

                    return values.stream().map(value -> {
                        WorkerTagEntity tag = new WorkerTagEntity();
                        tag.setWorkerTagId(idGenerator.generateId(IDType.WORKER_TAG));
                        tag.setWorkerId(workerId);
                        tag.setTagKey(entry.getKey());
                        tag.setTagValue(value);
                        return tag;
                    });
                })
                .collect(Collectors.toList());
    }


}
