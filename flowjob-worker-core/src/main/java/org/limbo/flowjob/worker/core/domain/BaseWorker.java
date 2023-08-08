/*
 * Copyright 2020-2024 Limbo Team (https://github.com/limbo-world).
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   	http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.limbo.flowjob.worker.core.domain;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.limbo.flowjob.common.exception.BrokerRpcException;
import org.limbo.flowjob.common.exception.RegisterFailException;
import org.limbo.flowjob.common.thread.NamedThreadFactory;
import org.limbo.flowjob.common.utils.SHAUtils;
import org.limbo.flowjob.common.utils.collections.MultiValueMap;
import org.limbo.flowjob.common.utils.collections.MutableMultiValueMap;
import org.limbo.flowjob.worker.core.constants.WorkerStatus;
import org.limbo.flowjob.worker.core.executor.ExecuteContext;
import org.limbo.flowjob.worker.core.executor.TaskExecutor;
import org.limbo.flowjob.worker.core.executor.TaskRepository;
import org.limbo.flowjob.worker.core.rpc.WorkerAgentRpc;
import org.limbo.flowjob.worker.core.rpc.WorkerBrokerRpc;
import org.limbo.flowjob.worker.core.rpc.http.OkHttpAgentRpc;

import java.net.URL;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 工作节点实例
 *
 * @author Devil
 * @since 2021/7/24
 */
@Slf4j
public class BaseWorker implements Worker {

    @Getter
    private final String name;

    /**
     * Worker 通信基础 URL
     */
    @Getter
    private URL rpcBaseURL;

    /**
     * 工作节点资源
     */
    @Getter
    private WorkerResources resource;

    /**
     * Worker 标签
     */
    private MultiValueMap<String, String, Set<String>> tags;

    /**
     * 执行器名称 - 执行器 映射关系
     */
    private final Map<String, TaskExecutor> executors;

    /**
     * 任务执行线程池
     */
    private ExecutorService threadPool;

    /**
     * 远程调用
     */
    private WorkerBrokerRpc brokerRpc;

    /**
     * 是否已经启动
     */
    private AtomicReference<WorkerStatus> status;

    /**
     * 心跳起搏器
     */
    private WorkerHeartbeat pacemaker;

    /**
     * 创建一个 Worker 实例
     * @param name worker 实例 名称，如未指定则会随机生成一个
     * @param baseURL worker 启动的 RPC 服务的 baseUrl
     * @param resource worker 资源描述对象
     * @param brokerRpc broker RPC 通信模块
     */
    public BaseWorker(String name, URL baseURL, WorkerResources resource, WorkerBrokerRpc brokerRpc) {
        Objects.requireNonNull(baseURL, "URL can't be null");
        Objects.requireNonNull(brokerRpc, "remote client can't be null");

        this.name = StringUtils.isBlank(name) ? SHAUtils.sha1AndHex(baseURL.toString()).toUpperCase() : name;
        this.rpcBaseURL = baseURL;
        this.resource = resource;
        this.brokerRpc = brokerRpc;

        Supplier<Set<String>> valueFactory = () -> Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.tags = new MutableMultiValueMap<>(new ConcurrentHashMap<>(), valueFactory);
        this.executors = new ConcurrentHashMap<>();
        this.status = new AtomicReference<>(WorkerStatus.IDLE);
    }


    /**
     * {@inheritDoc}
     * @return
     */
    @Override
    public WorkerTag getTags() {
        return new WorkerTag(this.tags);
    }


    /**
     * 添加 k=v 格式的 tag 表达式，如果 k 或 v 为空，则 tag 不会被添加。
     */
    public void addTag(String tag) {
        String[] tuple = tag.split("=");
        if (tuple.length < 2 || StringUtils.isAnyBlank(tuple)) {
            log.warn("Invalid tag form: {}", tag);
            return;
        }

        addTag(tuple[0], tuple[1]);
    }


    /**
     * {@inheritDoc}
     * @param key 标签 key
     * @param value 标签 value
     */
    @Override
    public void addTag(String key, String value) {
        this.tags.add(key, value);
    }


    /**
     * 添加任务执行器
     */
    @Override
    public void addExecutor(TaskExecutor executor) {
        Objects.requireNonNull(executor, "Executor can't be null");
        if (StringUtils.isBlank(executor.getName())) {
            throw new IllegalArgumentException("Executor.Name can't be null");
        }
        this.executors.put(executor.getName(), executor);
    }


    /**
     * 添加任务执行器
     */
    public void addExecutors(Collection<TaskExecutor> executors) {
        executors.forEach(this::addExecutor);
    }


    /**
     * 获取当前 Worker 中的执行器，不可修改
     */
    @Override
    public Map<String, TaskExecutor> getExecutors() {
        return Collections.unmodifiableMap(this.executors);
    }


    /**
     * 启动当前 Worker
     *
     * @param heartbeatPeriod 心跳间隔
     */
    @Override
    public void start(Duration heartbeatPeriod) {
        Objects.requireNonNull(heartbeatPeriod);

        // 重复检测
        if (!status.compareAndSet(WorkerStatus.IDLE, WorkerStatus.INITIALIZING)) {
            return;
        }

        Worker worker = this;

        Timer startTimer = new Timer();
        TimerTask startTask = new TimerTask() {
            @Override
            public void run() {
                // 状态检测
                if (WorkerStatus.INITIALIZING != status.get()) {
                    startTimer.cancel();
                    return;
                }
                // 注册
                try {
                    registerSelfToBroker();
                } catch (Exception e) {
                    log.error("Register to broker has error", e);
                    return;
                }

                // 启动心跳
                if (pacemaker == null) {
                    pacemaker = new WorkerHeartbeat(worker, Duration.ofSeconds(1));
                }
                pacemaker.start();

                // 初始化线程池
                BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(resource.queueSize() <= 0 ?
                        resource.concurrency() : resource.queueSize()
                );
                threadPool = new ThreadPoolExecutor(
                        resource.concurrency(), resource.concurrency(),
                        5, TimeUnit.SECONDS, queue,
                        NamedThreadFactory.newInstance("FlowJobWorkerTaskExecutor"),
                        (r, e) -> {
                            throw new RejectedExecutionException();
                        }
                );

                // 更新为运行中
                status.compareAndSet(WorkerStatus.INITIALIZING, WorkerStatus.RUNNING);
                log.info("worker start!");
            }
        };

        startTimer.scheduleAtFixedRate(startTask, 0, 3000);

    }


    /**
     * 向 Broker 注册当前 Worker
     */
    protected void registerSelfToBroker() {
        try {
            // 调用 Broker 远程接口，并更新 Broker 信息
            brokerRpc.register(this);
        } catch (RegisterFailException e) {
            log.error("Worker register failed", e);
            throw e;
        }

        log.info("register success!");
    }


    /**
     * Just beat it
     * 发送心跳
     */
    @Override
    public void sendHeartbeat() {
        try {
            brokerRpc.heartbeat(this);
        } catch (BrokerRpcException e) {
            log.warn("Worker send heartbeat failed");
            throw new IllegalStateException("Worker send heartbeat failed", e);
        }
    }


    /**
     * 接收 Broker 发送来的任务
     * @param task 任务数据
     */
    @Override
    public synchronized void receiveTask(Task task) {
        assertWorkerRunning();

        // 找到执行器，校验是否存在
        TaskExecutor executor = executors.get(task.getExecutorName());
        Objects.requireNonNull(executor, "Unsupported executor: " + task.getExecutorName());

        TaskRepository taskRepository = this.resource.taskRepository();
        int availableQueueSize = this.resource.availableQueueSize();
        if (taskRepository.count() >= availableQueueSize) {
            throw new IllegalArgumentException("Worker's queue is full, limit: " + availableQueueSize);
        }

        // 存储任务，并判断是否重复接收任务
        WorkerAgentRpc agentRpc = new OkHttpAgentRpc(); // todo 根据协议创建
        ExecuteContext context = new ExecuteContext(taskRepository, executor, agentRpc, task);
        if (!taskRepository.save(context)) {
            log.warn("Receive task [{}], but already in repository", task.getTaskId());
            return;
        }

        try {
            // 提交执行
            Future<?> future = this.threadPool.submit(context);
            context.setScheduleFuture(future);
        } catch (RejectedExecutionException e) {
            throw new IllegalStateException("Schedule task in worker failed, maybe work thread exhausted");
        }
    }


    /**
     * 验证 worker 正在运行中
     */
    private void assertWorkerRunning() {
        if (this.status.get() != WorkerStatus.RUNNING) {
            throw new IllegalStateException("Worker is not running: " + this.status.get());
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        // TODO ???
    }

}
