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

package org.limbo.flowjob.broker.core.exceptions;

import lombok.Getter;
import org.limbo.flowjob.common.exception.JobException;

/**
 * @author Brozen
 * @since 2021-05-21
 */
public class JobDispatchException extends JobException {

    private static final long serialVersionUID = -6143171637384399604L;

    /**
     * 作业实例ID
     */
    @Getter
    private final String id;

    public JobDispatchException(String jobId, String id, String message) {
        super(jobId, message);
        this.id = id;
    }

    public JobDispatchException(String jobId, String id, String message, Throwable cause) {
        super(jobId, message, cause);
        this.id = id;
    }

}
