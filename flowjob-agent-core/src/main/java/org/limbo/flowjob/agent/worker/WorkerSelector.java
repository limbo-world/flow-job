/*
 *
 *  * Copyright 2020-2024 Limbo Team (https://github.com/limbo-world).
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * 	http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.limbo.flowjob.agent.worker;

import org.limbo.flowjob.api.constants.LoadBalanceType;
import org.limbo.flowjob.common.lb.Invocation;

import java.util.List;

/**
 * worker选择器，封装了作业分发时的worker选择规则{@link LoadBalanceType}：
 * <ul>
 *     <li>{@link LoadBalanceType#ROUND_ROBIN}</li>
 *     <li>{@link LoadBalanceType#RANDOM}</li>
 *     <li>{@link LoadBalanceType#APPOINT}</li>
 *     <li>{@link LoadBalanceType#LEAST_FREQUENTLY_USED}</li>
 *     <li>{@link LoadBalanceType#LEAST_RECENTLY_USED}</li>
 *     <li>{@link LoadBalanceType#CONSISTENT_HASH}</li>
 * </ul>
 *
 * @author Brozen
 * @since 2021-05-14
 */
public interface WorkerSelector {

    /**
     * 选择作业上下文应当下发给的worker。
     *
     * @param invocation 选择内容
     * @param workers    待下发上下文可用的worker
     */
    Worker select(Invocation invocation, List<Worker> workers);

}
