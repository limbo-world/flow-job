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

package org.limbo.flowjob.api.constants.worker;

/**
 * @author Devil
 * @since 2022/11/7
 */
public interface HttpBrokerApi {

    String API_WORKER_REGISTER = "/api/v1/rpc/worker";

    String API_WORKER_HEARTBEAT = "/api/v1/rpc/worker/heartbeat";

    String API_WORKER_PLAN_SCHEDULE = "/api/v1/rpc/worker/plan/schedule";

    String API_WORKER_PLAN_INSTANCE_JOB_SCHEDULE = "/api/v1/rpc/worker/plan-instance/job/schedule";

    String API_WORKER_TASK_FEEDBACK = "/api/v1/rpc/worker/task/feedback";

}