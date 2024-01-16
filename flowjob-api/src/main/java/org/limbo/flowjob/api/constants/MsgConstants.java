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

package org.limbo.flowjob.api.constants;

/**
 * @author Devil
 * @since 2022/11/2
 */
public interface MsgConstants {

    String UNKNOWN = "Unknown";

    String EMPTY_TASKS = "empty tasks";

    String TASK_FAIL = "task fail";

    String DISPATCH_FAIL = "dispatch fail";

    String DISPATCH_FAIL_NO_AGENT = "dispatch fail no agent";

    String TERMINATE_BY_OTHER_JOB = "terminate by other job";

    String CANT_FIND_JOB_INSTANCE = "can't find job instance by id:";

    String CANT_FIND_PLAN_INSTANCE = "can't find plan instance by id:";

    String CANT_FIND_DELAY_INSTANCE = "can't find delay instance by id:";

    String CANT_FIND_PLAN = "can't find plan by id:";

    String CANT_FIND_PLAN_INFO = "can't find plan info by version:";

}
