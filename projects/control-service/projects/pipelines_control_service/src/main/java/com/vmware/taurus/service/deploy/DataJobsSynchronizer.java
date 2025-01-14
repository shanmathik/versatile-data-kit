/*
 * Copyright 2021-2023 VMware, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.vmware.taurus.service.deploy;

import com.vmware.taurus.service.JobsService;
import com.vmware.taurus.service.model.ActualDataJobDeployment;
import com.vmware.taurus.service.model.DataJob;
import com.vmware.taurus.service.model.DesiredDataJobDeployment;
import io.kubernetes.client.openapi.ApiException;
import lombok.AllArgsConstructor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * This class represents a utility for synchronizing Kubernetes CronJobs with data from a database
 * to maintain the desired state in the cluster.
 *
 * <p>It provides methods to retrieve job information from a database, compare it with the current
 * state of Kubernetes Jobs, and take actions to synchronize them. This can include creating new
 * CronJobs, updating existing CronJobs, etc.
 *
 * <p>Usage: - Create an instance of this class and call the `synchronizeDataJobs` method to
 * initiate the synchronization process.
 */
@AllArgsConstructor
@Component
public class DataJobsSynchronizer {

  private final JobsService jobsService;

  private final DeploymentServiceV2 deploymentService;

  /**
   * Synchronizes Kubernetes CronJobs from the database to ensure that the cluster's state matches
   * the desired state defined in the database records.
   *
   * <p>This method retrieves job information from the database, compares it with the current state
   * of Kubernetes CronJobs in the cluster, and takes the necessary actions to synchronize them.
   * This can include creating new CronJobs, updating existing CronJobs, etc.
   *
   * @throws ApiException
   */
  public void synchronizeDataJobs() throws ApiException {
    ThreadPoolTaskExecutor taskExecutor = initializeTaskExecutor();
    Iterable<DataJob> dataJobsFromDB = jobsService.findAllDataJobs();

    Set<String> dataJobDeploymentNamesFromKubernetes =
        deploymentService.findAllActualDeploymentNamesFromKubernetes();

    Map<String, DesiredDataJobDeployment> desiredDataJobDeploymentsFromDBMap =
        deploymentService.findAllDesiredDataJobDeployments();

    Map<String, ActualDataJobDeployment> actualDataJobDeploymentsFromDBMap =
        deploymentService.findAllActualDataJobDeployments();

    dataJobsFromDB.forEach(
        dataJob ->
            taskExecutor.execute(
                () ->
                    synchronizeDataJob(
                        dataJob,
                        desiredDataJobDeploymentsFromDBMap.get(dataJob.getName()),
                        actualDataJobDeploymentsFromDBMap.get(dataJob.getName()),
                        dataJobDeploymentNamesFromKubernetes.contains(dataJob.getName()))));

    taskExecutor.shutdown();
  }

  // Default for testing purposes
  void synchronizeDataJob(
      DataJob dataJob,
      DesiredDataJobDeployment desiredDataJobDeployment,
      ActualDataJobDeployment actualDataJobDeployment,
      boolean isDeploymentPresentInKubernetes) {
    if (desiredDataJobDeployment != null) {
      boolean sendNotification =
          true; // TODO [miroslavi] sends notification only when the deployment is initiated by the
      // user.
      deploymentService.updateDeployment(
          dataJob,
          desiredDataJobDeployment,
          actualDataJobDeployment,
          isDeploymentPresentInKubernetes,
          sendNotification);
    }
  }

  private ThreadPoolTaskExecutor initializeTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(10);
    executor.setQueueCapacity(Integer.MAX_VALUE);
    executor.setAllowCoreThreadTimeOut(true);
    executor.setKeepAliveSeconds(120);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.initialize();

    return executor;
  }
}
