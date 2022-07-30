package com.intellij.httpClient.http.request;

import com.intellij.httpClient.http.request.environment.HttpRequestEnvironment;
import com.intellij.httpClient.http.request.psi.HttpDynamicVariable;
import com.intellij.httpClient.http.request.psi.HttpRequestCompositeElement;
import com.intellij.httpClient.http.request.psi.HttpRequestElementTypes;
import com.intellij.httpClient.http.request.psi.HttpVariable;
import com.intellij.httpClient.http.request.psi.HttpVariableBase;
import com.intellij.httpClient.http.request.run.HttpClientDynamicVariables;
import com.intellij.httpClient.http.request.run.HttpRequestGlobalContext;
import com.intellij.httpClient.http.request.run.HttpRequestValidationException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class HttpRequestVariableSubstitutor {
    private static final Logger LOG = Logger.getInstance(HttpRequestVariableSubstitutor.class);
    private static final HttpRequestVariableSubstitutor EMPTY = new HttpRequestVariableSubstitutor(HttpRequestEnvironment.empty(), new HttpRequestGlobalContext());
    private final HttpRequestEnvironment myEnvironment;
    private final HttpRequestGlobalContext myGlobalContext;

    // 临时变量
    private final HttpRequestGlobalContext.HttpClientVariables variables = new HttpRequestGlobalContext.HttpClientVariables();

    private HttpRequestVariableSubstitutor(@NotNull HttpRequestEnvironment environment, @NotNull HttpRequestGlobalContext context) {

        super();
        this.myEnvironment = environment;
        this.myGlobalContext = context;
    }

    public static @NotNull HttpRequestVariableSubstitutor getDefault(@NotNull Project project) {
        return getDefault(project, (PsiFile)null);
    }

    public static @NotNull HttpRequestVariableSubstitutor getDefault(@NotNull Project project, @Nullable PsiFile contextFile) {
        try {
            HttpRequestEnvironment env = HttpRequestEnvironment.getDefault(project, contextFile);
            if (env != null) {
                return create(project, env);
            }
        } catch (HttpRequestValidationException var3) {
            LOG.debug(var3);
        }

        return empty();
    }

    public static @NotNull HttpRequestVariableSubstitutor create(@NotNull Project project, @NotNull HttpRequestEnvironment environment) {
        HttpRequestGlobalContext context = HttpRequestGlobalContext.getInstance(project);
        return new HttpRequestVariableSubstitutor(environment, context);
    }

    public static @NotNull HttpRequestVariableSubstitutor empty() {
        return EMPTY;
    }

    public @NotNull String getValue(@NotNull PsiElement element) {
        return this.getValue(element, Conditions.alwaysTrue());
    }

    public @NotNull String getValue(@NotNull PsiElement element, @NotNull Condition<? super PsiElement> filter) {

        if (element instanceof HttpVariableBase) {
            return this.getVariableValue((HttpVariableBase)element);
        } else {
            if (element instanceof HttpRequestCompositeElement) {
                StringBuilder builder = new StringBuilder();

                for(PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
                    if (child instanceof HttpVariableBase) {
                        builder.append(this.getVariableValue((HttpVariableBase)child));
                    } else if (filter.value(child)) {
                        builder.append(child.getText());
                    }
                }

                return builder.toString();
            } else {
                return element.getText();
            }
        }
    }

    private @NotNull String getVariableValue(@NotNull HttpVariableBase variable) {
        if (variable instanceof HttpDynamicVariable) {
            return this.getDynamicVariableValue(variable.getName(), variable.getText(), variable.getProject());
        } else if (variable instanceof HttpVariable) {
            return this.getEnvironmentVariableValue(variable.getName(), variable.getText());
        } else {
            throw new IllegalArgumentException("Unknown subclass of HttpVariableBase");
        }
    }

    @Contract("_, !null, _ -> !null")
    public @Nullable String getDynamicVariableValue(@Nullable String name, @Nullable String defaultValue, @NotNull Project project) {
        if (StringUtil.isNotEmpty(name)) {
            if (HttpClientDynamicVariables.hasProjectAwareVariable(name)) {
                return HttpClientDynamicVariables.get(name, project);
            }

            String result = HttpClientDynamicVariables.get(name);
            if (result != null) {
                return result;
            }
        }

        return defaultValue;
    }

    @Contract("_,!null->!null")
    public @Nullable String getEnvironmentVariableValue(@Nullable String name, @Nullable String defaultValue) {
        if (StringUtil.isNotEmpty(name)) {
            // 先取临时变量
            String variable = this.variables.get(name);
            if (variable != null) {
                return variable;
            }

            String global = this.myGlobalContext.getValue(name);
            if (global != null) {
                return global;
            }

            String envVariable = this.myEnvironment.getVariableValue(StringUtil.notNullize(name));
            if (envVariable != null) {
                return envVariable;
            }
        }

        return defaultValue;
    }

    @Contract("_,!null,_->!null")
    public @Nullable String getVariableValue(@Nullable String name, @Nullable String defaultValue, @NotNull Project project) {
        if (StringUtil.isNotEmpty(name)) {
            return name.startsWith(HttpRequestElementTypes.DYNAMIC_SIGN.toString()) ? this.getDynamicVariableValue(name.substring(HttpRequestElementTypes.DYNAMIC_SIGN.toString().length()).trim(), defaultValue, project) : this.getEnvironmentVariableValue(name, defaultValue);
        } else {
            return defaultValue;
        }
    }

    public @NotNull HttpRequestEnvironment getEnvironment() {
        return this.myEnvironment;
    }

    public HttpRequestGlobalContext.HttpClientVariables getGlobal() {
        return myGlobalContext.getGlobal();
    }

    public HttpRequestGlobalContext.HttpClientVariables getVariables() {
        return variables;
    }
}
