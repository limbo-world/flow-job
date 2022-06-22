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

package org.limbo.flowjob.broker.dao.test;

import lombok.Setter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.limbo.flowjob.broker.dao.entity.PlanEntity;
import org.limbo.flowjob.broker.dao.repositories.PlanJpaRepo;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.inject.Inject;
import java.util.Date;
import java.util.Optional;

/**
 * @author Devil
 * @since 2022/6/21
 */
@SpringBootTest
@RunWith(SpringRunner.class)
public class PlanTest {

    @Setter(onMethod_ = @Inject)
    private PlanJpaRepo planJpaRepo;

    @Test
    public void test() {
        PlanEntity plan = new PlanEntity();
        String planId = new Date().toString();
        plan.setId(planId);
        plan.setCurrentVersion("");
        plan.setRecentlyVersion("");
        plan.setIsEnabled(false);

        PlanEntity planEntity = planJpaRepo.save(plan);
        System.out.println(planEntity);
        Optional<PlanEntity> planEntityOptional = planJpaRepo.findById(planId);
        System.out.println(planEntityOptional.get());
    }
}