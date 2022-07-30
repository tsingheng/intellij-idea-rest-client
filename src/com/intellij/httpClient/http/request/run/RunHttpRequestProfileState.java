package com.intellij.httpClient.http.request.run;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.httpClient.execution.RestClientBundle;
import com.intellij.httpClient.http.request.HttpRequestVariableSubstitutor;
import com.intellij.httpClient.http.request.psi.HttpRequest;
import com.intellij.httpClient.http.request.run.config.HttpRequestExecutionConfig;
import com.intellij.httpClient.http.request.run.config.HttpRequestRunConfiguration;
import com.intellij.httpClient.http.request.run.console.HttpResponseConsole;
import com.intellij.httpClient.http.request.run.console.HttpResponsePresentation;
import com.intellij.httpClient.http.request.run.console.HttpSingleResponseConsole;
import com.intellij.httpClient.http.request.run.test.HttpMultiResponseConsole;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.ide.script.IdeScriptEngine;
import com.intellij.ide.script.IdeScriptEngineManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.SmartPointerManager;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.ApiStatus.Internal;

public class RunHttpRequestProfileState implements RunProfileState {
    private static final Logger logger = Logger.getInstance(HttpRunRequestInfo.class);
    private final Project myProject;
    private final HttpRequestExecutionConfig myConfig;
    private final SMTRunnerConsoleProperties myProperties;
    private final HttpRequestVariableSubstitutor mySubstitutor;
    @NotNull
    protected final HttpRequestRunConfiguration.Settings mySettings;

    private static final IdeScriptEngine engine = IdeScriptEngineManager.getInstance().getEngineByFileExtension("js", null);

    public RunHttpRequestProfileState(@NotNull Project project, @NotNull HttpRequestRunConfiguration.Settings settings, @NotNull HttpRequestExecutionConfig config, @NotNull SMTRunnerConsoleProperties properties, @NotNull HttpRequestVariableSubstitutor substitutor) {
        super();
        this.myProject = project;
        this.myConfig = config;
        this.myProperties = properties;
        this.mySubstitutor = substitutor;
        this.mySettings = settings;
    }

    public @Nullable ExecutionResult execute(Executor executor, @NotNull ProgramRunner<?> runner) throws ExecutionException {
        return this.execute();
    }

    @Internal
    public @Nullable ExecutionResult execute() throws ExecutionException {
        boolean hasResponseHandler = this.myConfig.getRequests().stream().anyMatch((requestx) -> {
            return requestx.getResponseHandler() != null;
        });
        HttpClientRequestProcessHandler processHandler = new HttpClientRequestProcessHandler(hasResponseHandler);
        List<HttpRunRequestInfo> infos = new ArrayList();
        Iterator var4 = this.myConfig.getRequests().iterator();
        while(var4.hasNext()) {
            HttpRequest request = (HttpRequest)var4.next();
            infos.add(HttpRunRequestInfo.create(request, SmartPointerManager.createPointer(request), this.mySubstitutor));
        }

        final HttpResponseConsole console = this.createConsole(processHandler, infos);
        processHandler.addProcessListener(new ProcessAdapter() {
            public void processTerminated(@NotNull ProcessEvent event) {
                int exitCode = event.getExitCode();
                if (exitCode != 0) {
                    String message = exitCode == 1 ? RestClientBundle.message("rest.client.request.execute.cancel", new Object[0]) : RestClientBundle.message("rest.client.request.execute.cancel.post.process", new Object[0]);
                    console.setErrorResponse((String)null, HttpResponsePresentation.createErrorResponse(message));
                }

            }
        });

        for (HttpRequest psiRequest : this.myConfig.getRequests()) {
            // 判断是否有前置脚本需要执行
            if (psiRequest.getContainingFile() != null && !ScratchUtil.isScratch(psiRequest.getContainingFile().getVirtualFile())) {
                // 查找同级目录或上级目录中文件名为pre-request-script.js的文件
                String projectPath = psiRequest.getProject().getBasePath();
                VirtualFile searchDir = psiRequest.getContainingFile().getVirtualFile().getParent();
                VirtualFile preRequestScriptFile = searchPreRequestScript(projectPath, searchDir);
                if (preRequestScriptFile != null) {
                    // 执行前置脚本
                    try (FileReader reader = new FileReader(preRequestScriptFile.getPath())) {
                        engine.setBinding("variables", this.mySubstitutor.getVariables());
                        engine.setBinding("global", this.mySubstitutor.getGlobal());
                        engine.setBinding("environment", this.mySubstitutor.getEnvironment());
                        engine.setBinding("request", psiRequest);
                        engine.setBinding("project", projectPath);
                        engine.setBinding("console", console.getConsole());
                        engine.setBinding("consoleViewContentTypeInfo", ConsoleViewContentType.LOG_INFO_OUTPUT);
                        engine.eval(IOUtils.toString(reader));
                    } catch (Exception e) {
                        Messages.showErrorDialog(e.getMessage(), "ERROR");
                    }
                }
            }
        }

        this.executeHttpRequest(this.myProject, infos.iterator(), processHandler, console, infos.size() == 1);
        return new DefaultExecutionResult(console.getConsole(), processHandler);
    }

    private static VirtualFile searchPreRequestScript(String projectPath, VirtualFile searchDir) {
        if (searchDir.getPath().startsWith(projectPath)) {
            VirtualFile jsFile = searchDir.findChild("pre-request-script.js");
            if (jsFile != null) {
                return jsFile;
            }
            return searchPreRequestScript(projectPath, searchDir.getParent());
        }
        return null;
    }

    private @NotNull HttpResponseConsole createConsole(@NotNull HttpClientRequestProcessHandler processHandler, @NotNull List<HttpRunRequestInfo> requests) throws ExecutionException {
        if (requests.isEmpty()) {
            throw new ExecutionException(RestClientBundle.message("http.request.no.requests.to.execute.error", new Object[0]));
        } else if (requests.size() == 1) {
            HttpRunRequestInfo info = (HttpRunRequestInfo)requests.get(0);
            return new HttpSingleResponseConsole(this.myProject, this.myProperties, processHandler, this.myConfig.isShowInformationAboutRequest(), info);
        } else {
            return new HttpMultiResponseConsole(this.myProject, this.myProperties, processHandler);
        }
    }

    private void executeHttpRequest(@NotNull Project project, @NotNull Iterator<HttpRunRequestInfo> requests, @NotNull HttpClientRequestProcessHandler processHandler, @NotNull HttpResponseConsole console, boolean showResponseInplace) {
        if (requests.hasNext()) {
            try {
                this.createExecutionController(project, requests, processHandler, console, showResponseInplace).execute();
            } catch (ExecutionException var7) {
                HttpRequestNotifications.showErrorBalloon(project, RestClientBundle.message("rest.client.request.execute.notification", new Object[0]), var7.getMessage());
                processHandler.onRunFinished();
            }

        }
    }

    protected @NotNull HttpClientExecutionController createExecutionController(@NotNull Project project, @NotNull Iterator<HttpRunRequestInfo> requests, @NotNull HttpClientRequestProcessHandler processHandler, @NotNull HttpResponseConsole console, boolean showResponseInplace) throws ExecutionException {
        Runnable onRequestFinished = this.createOnFinished(project, console, processHandler, requests, showResponseInplace);
        return HttpClientExecutionController.create(project, (HttpRunRequestInfo)requests.next(), processHandler, console, onRequestFinished, showResponseInplace, this.mySettings, false);
    }

    protected @NotNull Runnable createOnFinished(@NotNull Project project, @NotNull HttpResponseConsole console, @NotNull HttpClientRequestProcessHandler processHandler, @NotNull Iterator<HttpRunRequestInfo> requests, boolean showResponseInplace) {

        return () -> {
            if (requests.hasNext()) {
                this.executeHttpRequest(project, requests, processHandler, console, showResponseInplace);
            } else {
                processHandler.onRunFinished();
            }

        };
    }
}
