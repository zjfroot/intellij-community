/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.externalSystem.build;

import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskDescriptor;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Abstraction layer for executing gradle tasks.
 * 
 * @author Denis Zhdanov
 * @since 3/14/13 5:04 PM
 */
public interface ExternalSystemBuildManager<S extends ExternalSystemExecutionSettings> {

  /**
   * Refreshes available tasks for the target external project.
   *  
   * @param id                          request task id
   * @param projectPath                 target external project path
   * @param settings                    settings to use during the refresh
   * @return                            mappings like 'module name -&gt; module tasks'
   * @throws ExternalSystemException    in case when unexpected exception occurs during project info construction
   */
  Map<String, Collection<ExternalSystemTaskDescriptor>> listTasks(@NotNull ExternalSystemTaskId id,
                                                                  @NotNull String projectPath,
                                                                  @Nullable S settings)
    throws ExternalSystemException;
  
  void executeTasks(@NotNull ExternalSystemTaskId id, @NotNull List<String> taskNames, @NotNull String projectPath, @Nullable S settings)
    throws ExternalSystemException;
}
