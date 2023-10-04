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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 *
 * @author Brozen
 * @since 2021-05-19
 */
public enum JobType {

    UNKNOWN(ConstantsPool.UNKNOWN, "未知"),
    /**
     * 给一个节点下发的任务
     */
    STANDALONE(1, "单机任务"),
    /**
     * 给每个可选中节点下发任务
     */
    BROADCAST(2, "广播任务"),
    /**
     * Map任务
     * 拆分任务->处理分片
     */
    MAP(3, "Map任务"),
    /**
     * MapReduce任务
     * 拆分任务->处理分片->最终处理
     */
    MAP_REDUCE(4, "MapReduce任务"),
    ;

    @JsonValue
    public final int type;

    @Getter
    public final String desc;

    JobType(int type, String desc) {
        this.type = type;
        this.desc = desc;
    }

    /**
     * 校验是否是当前状态
     *
     * @param type 待校验值
     */
    public boolean is(JobType type) {
        return equals(type);
    }

    /**
     * 校验是否是当前状态
     *
     * @param type 待校验状态值
     */
    public boolean is(Number type) {
        return type != null && type.intValue() == this.type;
    }

    /**
     * 解析上下文状态值
     */
    @JsonCreator
    public static JobType parse(Number type) {
        for (JobType jobType : values()) {
            if (jobType.is(type)) {
                return jobType;
            }
        }
        return UNKNOWN;
    }

}
