/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.WeighedFileEditorProvider;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
@State(name = "FileEditorProviderManager", storages = {@Storage( file = StoragePathMacros.APP_CONFIG + "/other.xml")})
public final class FileEditorProviderManagerImpl extends FileEditorProviderManager implements PersistentStateComponent<FileEditorProviderManagerImpl> {
  private static final FileEditorProvider[] EMPTY_ARRAY=new FileEditorProvider[]{};
  private static final String SEPARATOR = ",";

  private final ArrayList<FileEditorProvider> myProviders = new ArrayList<FileEditorProvider>();
  private final ArrayList<FileEditorProvider> mySharedProviderList = new ArrayList<FileEditorProvider>();

  public FileEditorProviderManagerImpl(FileEditorProvider[] providers) {
    Extensions.getRootArea().getExtensionPoint(FileEditorProvider.EP_FILE_EDITOR_PROVIDER).addExtensionPointListener(
      new ExtensionPointListener<FileEditorProvider>() {
        public void extensionAdded(@NotNull final FileEditorProvider extension, @Nullable final PluginDescriptor pluginDescriptor) {
          registerProvider(extension);
        }

        public void extensionRemoved(@NotNull final FileEditorProvider extension, @Nullable final PluginDescriptor pluginDescriptor) {
          unregisterProvider(extension);
        }
      });

    for (FileEditorProvider provider : providers) {
      registerProvider(provider);
    }
  }

  public FileEditorProviderManagerImpl() {
  }

  @NotNull
  public synchronized FileEditorProvider[] getProviders(@NotNull Project project, @NotNull VirtualFile file){
    // Collect all possible editors
    mySharedProviderList.clear();
    boolean doNotShowTextEditor = false;
    final boolean dumb = DumbService.getInstance(project).isDumb();
    for(int i = myProviders.size() -1 ; i >= 0; i--){
      FileEditorProvider provider=myProviders.get(i);
      if((!dumb || DumbService.isDumbAware(provider)) && provider.accept(project, file)){
        mySharedProviderList.add(provider);
        doNotShowTextEditor |= provider.getPolicy() == FileEditorPolicy.HIDE_DEFAULT_EDITOR;
      }
    }

    // Throw out default editors provider if necessary
    if(doNotShowTextEditor){
      for(int i = mySharedProviderList.size() - 1; i >= 0; i--){
        if(mySharedProviderList.get(i) instanceof TextEditorProvider){
          mySharedProviderList.remove(i);
        }
      }
    }

    // Sort editors according policies
    Collections.sort(mySharedProviderList, MyComparator.ourInstance);

    if(!mySharedProviderList.isEmpty()){
      return mySharedProviderList.toArray(new FileEditorProvider[mySharedProviderList.size()]);
    }
    else{
      return EMPTY_ARRAY;
    }
  }

  @Nullable
  public synchronized FileEditorProvider getProvider(@NotNull String editorTypeId){
    for(int i=myProviders.size()-1;i>=0;i--){
      FileEditorProvider provider=myProviders.get(i);
      if(provider.getEditorTypeId().equals(editorTypeId)){
        return provider;
      }
    }
    return null;
  }

  private void registerProvider(FileEditorProvider provider) {
    String editorTypeId = provider.getEditorTypeId();
    for(int i=myProviders.size()-1;i>=0;i--){
      FileEditorProvider _provider=myProviders.get(i);
      if(editorTypeId.equals(_provider.getEditorTypeId())){
        throw new IllegalArgumentException(
          "attempt to register provider with non unique editorTypeId: "+_provider.getEditorTypeId()
        );
      }
    }
    myProviders.add(provider);
  }

  private void unregisterProvider(FileEditorProvider provider) {
    final boolean b = myProviders.remove(provider);
    assert b;
  }

  @Nullable
  @Override
  public FileEditorProviderManagerImpl getState() {
    return this;
  }

  @Override
  public void loadState(FileEditorProviderManagerImpl state) {
    mySelectedProviders.clear();
    mySelectedProviders.putAll(state.mySelectedProviders);
  }

  private static final Function<FileEditorProvider,String> EDITOR_PROVIDER_STRING_FUNCTION = new Function<FileEditorProvider, String>() {
    @Override
    public String fun(FileEditorProvider provider) {
      return provider.getEditorTypeId();
    }
  };

  private final Map<String, String> mySelectedProviders = new HashMap<String, String>();

  void providerSelected(EditorComposite composite) {
    if (!(composite instanceof EditorWithProviderComposite)) return;
    FileEditorProvider[] providers = ((EditorWithProviderComposite)composite).getProviders();
    if (providers.length < 2) return;
    mySelectedProviders.put(computeKey(providers),
                            composite.getSelectedEditorWithProvider().getSecond().getEditorTypeId());
  }

  private static String computeKey(FileEditorProvider[] providers) {
    return StringUtil.join(ContainerUtil.map(providers, EDITOR_PROVIDER_STRING_FUNCTION), SEPARATOR);
  }

  @Nullable
  FileEditorProvider getSelectedFileEditorProvider(EditorHistoryManager editorHistoryManager,
                                                   VirtualFile file,
                                                   FileEditorProvider[] providers) {
    FileEditorProvider provider = editorHistoryManager.getSelectedProvider(file);
    if (provider != null || providers.length < 2) {
      return provider;
    }
    String id = mySelectedProviders.get(computeKey(providers));
    return id == null ? null : getProvider(id);
  }

  @MapAnnotation(surroundKeyWithTag = false, surroundValueWithTag = false)
  public Map<String, String> getSelectedProviders() {
    return mySelectedProviders;
  }

  public void setSelectedProviders(Map<String, String> selectedProviders) {
    mySelectedProviders.clear();
    mySelectedProviders.putAll(selectedProviders);
  }

  @TestOnly
  public void clearSelectedProviders() {
    mySelectedProviders.clear();
  }

  private static final class MyComparator implements Comparator<FileEditorProvider>{
    public static final MyComparator ourInstance = new MyComparator();

    private static double getWeight(FileEditorProvider provider) {
      return provider instanceof WeighedFileEditorProvider
             ? ((WeighedFileEditorProvider) provider).getWeight()
             : Double.MAX_VALUE;
    }

    public int compare(FileEditorProvider provider1, FileEditorProvider provider2) {
      final int i1 = provider1.getPolicy().ordinal();
      final int i2 = provider2.getPolicy().ordinal();
      if (i1 != i2) return i1 - i2;
      final double value = getWeight(provider1) - getWeight(provider2);
      return value > 0 ? 1 : value < 0 ? -1 : 0;
    }
  }
}
