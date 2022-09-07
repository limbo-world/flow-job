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

package org.limbo.flowjob.broker.core.worker.rpc;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.limbo.flowjob.broker.api.constants.enums.WorkerProtocol;
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
public abstract class HttpWorkerRpc implements WorkerRpc {


    public static final String API_PING = "/api/worker/v1/ping";
    public static final String API_SEND_TASK = "/api/worker/v1/task";

    /**
     * URL 缓存
     */
    private static final Map<String, URL> URL_CACHE = new ConcurrentHashMap<>();

    /**
     * 此 RPC 绑定到的 Worker ID
     */
    @Getter(AccessLevel.PROTECTED)
    private String workerId;

    /**
     * worker服务使用的通信协议，默认为Http协议。
     * @see WorkerProtocol
     */
    private WorkerProtocol protocol;

    /**
     * worker服务的通信host
     */
    private String host;

    /**
     * worker服务的通信端口
     */
    private Integer port;

    /**
     * protocol、host、port 组合成的基础 URL
     */
    @Getter(AccessLevel.PROTECTED)
    private URL baseUrl;


    public HttpWorkerRpc(Worker worker) {
        this.workerId = worker.getWorkerId();
        this.baseUrl = worker.getRpcBaseUrl();
    }


    /**
     * 组装 baseUrl，并检查 RPC 通信参数是否合法
     */
    private URL baseUrl() {
        try {
            return new URL(this.protocol.protocol, this.host, this.port, "");
        } catch (MalformedURLException ignore) {
            log.error("wrong RPC param：protocol={} host={} port={}", this.protocol, this.host, this.port);
            throw new IllegalArgumentException("wrong RPC param：protocol="
                    + this.protocol + " host=" + this.host + " port=" + this.port);
        }
    }


    /**
     * 将接口转换为 URL 格式，拼接 worker 的通信协议
     */
    private URL toURL(String api) {
        return URL_CACHE.computeIfAbsent(api, _api -> {
            try {
                return new URL(this.protocol.protocol, this.host, this.port, _api);
            } catch (MalformedURLException ignore) {
                log.error("unexpected URL form while worker RPC：{}", _api);
                throw new IllegalArgumentException("unexpected URL form while worker RPC：" + _api);
            }
        });
    }

}