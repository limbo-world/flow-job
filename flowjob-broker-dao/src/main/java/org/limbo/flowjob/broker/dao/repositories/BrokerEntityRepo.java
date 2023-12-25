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

package org.limbo.flowjob.broker.dao.repositories;

import org.limbo.flowjob.broker.dao.entity.BrokerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * @author Devil
 * @since 2022/7/18
 */
public interface BrokerEntityRepo extends JpaRepository<BrokerEntity, String> {

    BrokerEntity findByProtocolAndHostAndPort(String protocol, String host, Integer port);

    List<BrokerEntity> findByLastHeartbeatBetween(LocalDateTime startTime, LocalDateTime endTime);

    List<BrokerEntity> findByOnlineTimeBetween(LocalDateTime startTime, LocalDateTime endTime);
}
