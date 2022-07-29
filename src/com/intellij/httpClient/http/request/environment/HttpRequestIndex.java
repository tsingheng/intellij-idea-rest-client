//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.intellij.httpClient.http.request.environment;

import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.ide.scratch.ScratchesSearchScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.indexing.FileBasedIndex;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class HttpRequestIndex {
    public HttpRequestIndex() {
    }

    public static @NotNull Collection<String> getAllEnvironments(@NotNull Project project, @Nullable PsiFile contextFile) {
        // 重写默认逻辑,只选择当前工程目录下的环境
        if (contextFile.getVirtualFile().getPath().startsWith(project.getBasePath())) {
            String uri = contextFile.getVirtualFile().getPath().replace(project.getBasePath() + "/", "");
            String prefix = uri.substring(0, uri.indexOf("/"));
            return getAllEnvironments(project, getSearchScope(project, contextFile))
                    .stream()
                    .filter(env -> env.startsWith(prefix + ":"))
                    .collect(Collectors.toList());
        }
        return getAllEnvironments(project, getSearchScope(project, contextFile));

    }

    public static @NotNull Collection<String> getAllEnvironments(@NotNull Project project, @NotNull GlobalSearchScope scope) {

        FileBasedIndex index = FileBasedIndex.getInstance();
        Collection var10000 = (Collection)index.getAllKeys(HttpRequestEnvironmentIndex.INDEX_ID, project).stream().filter((env) -> {
            return !HttpClientSelectedEnvironments.isSelectBeforeRun(env);
        }).filter((env) -> {
            return !index.getContainingFiles(HttpRequestEnvironmentIndex.INDEX_ID, env, scope).isEmpty();
        }).collect(Collectors.toSet());
        return var10000;
    }

    public static Collection<VirtualFile> getEnvironmentFiles(@NotNull Project project, @NotNull String env, @Nullable PsiFile contextFile) {

        GlobalSearchScope scope = getSearchScope(project, contextFile);
        return FileBasedIndex.getInstance().getContainingFiles(HttpRequestEnvironmentIndex.INDEX_ID, env, scope);
    }

    public static @NotNull Collection<String> getAllVariables(@NotNull Project project, @Nullable PsiFile contextFile) {
        GlobalSearchScope scope = getSearchScope(project, contextFile);
        Collection<String> environments = getAllEnvironments(project, scope);
        if (environments.isEmpty()) {
            Set var10000 = Collections.emptySet();

            return var10000;
        } else {
            Set<String> variables = new HashSet();
            Iterator var5 = environments.iterator();

            while(var5.hasNext()) {
                String env = (String)var5.next();
                List<Set<String>> varsInEnv = FileBasedIndex.getInstance().getValues(HttpRequestEnvironmentIndex.INDEX_ID, env, scope);
                Iterator var8 = varsInEnv.iterator();

                while(var8.hasNext()) {
                    Set<String> vars = (Set)var8.next();
                    variables.addAll(vars);
                }
            }

            return variables;
        }
    }

    public static @NotNull Stream<String> getAllVariables(@NotNull Project project, @NotNull String env, @Nullable PsiFile contextFile) {

        List<Set<String>> variables = FileBasedIndex.getInstance().getValues(HttpRequestEnvironmentIndex.INDEX_ID, env, getSearchScope(project, contextFile));
        Stream var10000 = variables.stream().flatMap(Collection::stream).distinct();

        return var10000;
    }

    public static @NotNull GlobalSearchScope getSearchScope(@NotNull Project project, @Nullable PsiFile contextFile) {

        GlobalSearchScope projectScope = ProjectScope.getContentScope(project);
        VirtualFile context = PsiUtilCore.getVirtualFile(contextFile);
        GlobalSearchScope var10000;
        if (contextFile != null && context != null && !ScratchUtil.isScratch(context) && !projectScope.contains(context)) {
            PsiDirectory parent = contextFile.getParent();
            if (parent != null) {
                var10000 = GlobalSearchScopesCore.directoryScope(parent, false);

                return var10000;
            }
        }

        if (ScratchUtil.isScratch(context)) {
            var10000 = projectScope.uniteWith(ScratchesSearchScope.getScratchesScope(project));

            return var10000;
        } else {

            return projectScope;
        }
    }
}
