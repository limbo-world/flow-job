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

package org.limbo.flowjob.worker.starter.configuration;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.limbo.flowjob.api.constants.Protocol;
import org.limbo.flowjob.common.lb.BaseLBServer;
import org.limbo.flowjob.common.lb.BaseLBServerRepository;
import org.limbo.flowjob.common.lb.LBServerRepository;
import org.limbo.flowjob.common.lb.LBStrategy;
import org.limbo.flowjob.common.lb.strategies.RoundRobinLBStrategy;
import org.limbo.flowjob.common.rpc.EmbedHttpRpcServer;
import org.limbo.flowjob.common.rpc.EmbedRpcServer;
import org.limbo.flowjob.common.utils.NetUtils;
import org.limbo.flowjob.worker.core.domain.BaseWorker;
import org.limbo.flowjob.worker.core.domain.Worker;
import org.limbo.flowjob.worker.core.domain.WorkerResources;
import org.limbo.flowjob.worker.core.resource.CalculatingWorkerResource;
import org.limbo.flowjob.worker.core.rpc.WorkerAgentRpc;
import org.limbo.flowjob.worker.core.rpc.WorkerBrokerRpc;
import org.limbo.flowjob.worker.core.rpc.http.OkHttpAgentRpc;
import org.limbo.flowjob.worker.core.rpc.http.OkHttpBrokerRpc;
import org.limbo.flowjob.worker.starter.SpringDelegatedWorker;
import org.limbo.flowjob.worker.starter.handler.HttpHandlerProcessor;
import org.limbo.flowjob.worker.starter.processor.ExecutorMethodProcessor;
import org.limbo.flowjob.worker.starter.properties.WorkerProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Brozen
 * @since 2022-09-05
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "flowjob.worker", value = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WorkerProperties.class)
public class FlowJobWorkerAutoConfiguration {

    private final WorkerProperties workerProps;

    //    @Setter(onMethod_ = @Value("${server.port:8080}")) // 不依赖主应用的端口 默认设置一个
    private static final Integer DEFAULT_HTTP_SERVER_PORT = 9877;

    public FlowJobWorkerAutoConfiguration(WorkerProperties workerProps) {
        this.workerProps = workerProps;
    }


    /**
     * 用于扫描 @Executor 注解标记的方法
     */
    @Bean
    public ExecutorMethodProcessor executorMethodProcessor() {
        return new ExecutorMethodProcessor(workerProps.isAutoRegister());
    }


    /**
     * Worker 实例，
     *
     * @param brokerRpc broker rpc 通信模块
     */
    @Bean
    public Worker httpWorker(WorkerResources resources, WorkerBrokerRpc brokerRpc, WorkerAgentRpc agentRpc) throws MalformedURLException {
        // 优先使用 SpringMVC 或 SpringWebflux 设置的端口号
        Integer port = workerProps.getPort() != null ? workerProps.getPort() : DEFAULT_HTTP_SERVER_PORT;

        // 优先使用指定的 host，如未指定则自动寻找本机 IP
        String host = workerProps.getHost();
        if (StringUtils.isEmpty(host)) {
            host = NetUtils.getLocalIp();
        }

        Assert.isTrue(port > 0, "Worker port must be a positive integer in range 1 ~ 65534");
        URL workerBaseUrl = new URL(workerProps.getProtocol().getValue(), host, port, "");
        HttpHandlerProcessor httpHandlerProcessor = new HttpHandlerProcessor();
        EmbedRpcServer embedRpcServer = new EmbedHttpRpcServer(port, httpHandlerProcessor);

        // worker
        BaseWorker worker = new BaseWorker(workerProps.getName(), workerBaseUrl, resources, brokerRpc, agentRpc, embedRpcServer);
        httpHandlerProcessor.setWorker(worker);
        brokerRpc.setWorker(worker);
        agentRpc.setWorker(worker);

        // 将 tag 添加到 Worker
        if (CollectionUtils.isNotEmpty(workerProps.getTags())) {
            workerProps.getTags().forEach(worker::addTag);
        }

        return new SpringDelegatedWorker(worker);
    }


    /**
     * 动态计算 Worker 资源
     */
    @Bean
    @ConditionalOnMissingBean(WorkerResources.class)
    public WorkerResources calculatingWorkerResource() {
        return new CalculatingWorkerResource(workerProps.getConcurrency(), workerProps.getQueueSize());
    }

    /**
     * Broker 通信模块
     */
    @Bean
    @ConditionalOnMissingBean(WorkerBrokerRpc.class)
    public WorkerBrokerRpc brokerRpc(LBServerRepository<BaseLBServer> fjwBrokerLoadBalanceRepo, LBStrategy<BaseLBServer> fjwBrokerLoadBalanceStrategy) {
        List<URL> brokers = workerProps.getBrokers();
        if (CollectionUtils.isEmpty(brokers)) {
            throw new IllegalArgumentException("No brokers configured");
        }

        // HTTP、HTTPS 协议
        String brokerProtocol = brokers.get(0).getProtocol();
        if (Protocol.parse(brokerProtocol) == Protocol.UNKNOWN) {
            throw new IllegalArgumentException("Unsupported broker protocol [" + brokerProtocol + "]");
        }

        return httpBrokerRpc(fjwBrokerLoadBalanceRepo, fjwBrokerLoadBalanceStrategy);
    }

    /**
     * Agent 通信模块
     */
    @Bean
    @ConditionalOnMissingBean(WorkerAgentRpc.class)
    public WorkerAgentRpc agentRpc() {
        return new OkHttpAgentRpc();
    }

    /**
     * HTTP 协议的 broker 通信
     */
    private OkHttpBrokerRpc httpBrokerRpc(LBServerRepository<BaseLBServer> loadBalancer, LBStrategy<BaseLBServer> strategy) {
        return new OkHttpBrokerRpc(loadBalancer, strategy);
    }

    private List<BaseLBServer> brokerNodes() {
        List<URL> brokerUrls = workerProps.getBrokers() == null ? Collections.emptyList() : workerProps.getBrokers();
        return brokerUrls.stream()
                .map(BaseLBServer::new)
                .collect(Collectors.toList());
    }


    /**
     * Broker 仓储
     */
    @Bean("fjwBrokerLoadBalanceRepo")
    @ConditionalOnMissingBean(name = "fjwBrokerLoadBalanceRepo")
    public LBServerRepository<BaseLBServer> brokerLoadBalanceRepo() {
        return new BaseLBServerRepository<>(brokerNodes());
    }


    /**
     * Broker 负载均衡策略
     */
    @Bean("fjwBrokerLoadBalanceStrategy")
    @ConditionalOnMissingBean(name = "fjwBrokerLoadBalanceStrategy")
    public LBStrategy<BaseLBServer> brokerLoadBalanceStrategy() {
        return new RoundRobinLBStrategy<>();
    }

}
