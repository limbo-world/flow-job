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

package org.limbo.flowjob.worker.core.rpc;

import org.limbo.flowjob.worker.core.executor.ExecuteContext;

import javax.annotation.Nullable;

/**
 * @author Devil
 * @since 2023/8/7
 */
public interface WorkerAgentRpc {

    /**
     * 反馈任务执行成功
     * @param context 任务执行上下文
     */
    void feedbackTaskSucceed(ExecuteContext context);


    /**
     * 反馈任务执行失败
     * @param context 任务执行上下文
     * @param ex 导致任务失败的异常信息，可以为 null
     */
    void feedbackTaskFailed(ExecuteContext context, @Nullable Throwable ex);
}