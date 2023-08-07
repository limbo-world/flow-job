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

package org.limbo.flowjob.broker.core.agent.rpc;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.limbo.flowjob.broker.core.rpc.AbstractRpc;
import org.limbo.flowjob.broker.core.worker.Worker;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Brozen
 * @since 2022-08-12
 */
@Slf4j
public abstract class HttpAgentRpc extends AbstractRpc implements AgentRpc {

    /**
     * URL 缓存
     */
    private static final Map<String, URL> URL_CACHE = new ConcurrentHashMap<>();

    /**
     * protocol、host、port 组合成的基础 URL
     */
    @Getter(AccessLevel.PROTECTED)
    private URL baseUrl;


    public HttpAgentRpc(Worker worker) {
        super(worker.getId());
        this.baseUrl = worker.getRpcBaseUrl();
    }


    /**
     * 组装 baseUrl，并检查 RPC 通信参数是否合法
     */
    private URL baseUrl() {
        try {
            return new URL(protocol().value, host(), port(), "");
        } catch (MalformedURLException ignore) {
            log.error("wrong RPC param：protocol={} host={} port={}", protocol().value, host(), port());
            throw new IllegalArgumentException("wrong RPC param：protocol="
                    + protocol().value + " host=" + host() + " port=" + port());
        }
    }


    /**
     * 将接口转换为 URL 格式，拼接 worker 的通信协议
     */
    private URL toURL(String api) {
        return URL_CACHE.computeIfAbsent(api, newApi -> {
            try {
                return new URL(protocol().value, host(), port(), newApi);
            } catch (MalformedURLException ignore) {
                log.error("unexpected URL form while worker RPC：{}", newApi);
                throw new IllegalArgumentException("unexpected URL form while worker RPC：" + newApi);
            }
        });
    }

}
