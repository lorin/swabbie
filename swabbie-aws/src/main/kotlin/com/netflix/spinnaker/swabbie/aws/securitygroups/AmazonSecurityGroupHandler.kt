/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.swabbie.aws.securitygroups

import com.netflix.spinnaker.moniker.frigga.FriggaReflectiveNamer
import com.netflix.spinnaker.swabbie.configuration.ScopeOfWorkConfiguration
import com.netflix.spinnaker.swabbie.ResourceTrackingRepository
import com.netflix.spinnaker.swabbie.AbstractResourceHandler
import com.netflix.spinnaker.swabbie.model.*
import com.netflix.spinnaker.swabbie.orca.OrcaJob
import com.netflix.spinnaker.swabbie.orca.OrcaService
import com.netflix.spinnaker.swabbie.orca.OrchestrationRequest
import com.netflix.spinnaker.swabbie.SecurityGroupProvider
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class AmazonSecurityGroupHandler(
  clock: Clock,
  rules: List<Rule>,
  resourceTrackingRepository: ResourceTrackingRepository,
  applicationEventPublisher: ApplicationEventPublisher,
  private val securityGroupProvider: SecurityGroupProvider,
  private val orcaService: OrcaService
): AbstractResourceHandler(clock, rules, resourceTrackingRepository, applicationEventPublisher) {
  override fun remove(markedResource: MarkedResource, scopeOfWorkConfiguration: ScopeOfWorkConfiguration) {
    markedResource.resource.let { resource ->
      if (resource is AmazonSecurityGroup) {
        log.info("This resource is about to be deleted {}", markedResource)
        orcaService.orchestrate(
          OrchestrationRequest(
            application = FriggaReflectiveNamer().deriveMoniker(markedResource).app,
            job = listOf(
              OrcaJob(
                type = "deleteSecurityGroup",
                context = mutableMapOf(
                  "credentials" to scopeOfWorkConfiguration.account.name,
                  "securityGroupName" to resource.groupName,
                  "cloudProvider" to resource.cloudProvider,
                  "vpcId" to resource.vpcId,
                  "regions" to listOf(scopeOfWorkConfiguration.location)
                )
              )
            ),
            description = "Swabbie delete security group ${FriggaReflectiveNamer().deriveMoniker(markedResource).app}"
          )
        )
      }
    }
  }

  override fun getUpstreamResource(markedResource: MarkedResource, scopeOfWorkConfiguration: ScopeOfWorkConfiguration): Resource?
    = securityGroupProvider.getSecurityGroup(
      groupId = markedResource.resourceId,
      account = scopeOfWorkConfiguration.account.name,
      region = scopeOfWorkConfiguration.location
    )

  override fun handles(resourceType: String, cloudProvider: String): Boolean = resourceType == SECURITY_GROUP && cloudProvider == AWS

  override fun getUpstreamResources(scopeOfWorkConfiguration: ScopeOfWorkConfiguration): List<Resource>?
    = securityGroupProvider.getSecurityGroups(
      account = scopeOfWorkConfiguration.account.name,
      region = scopeOfWorkConfiguration.location
    )
}