# P0 安全加固实施计划

> **供执行代理使用：** 必须使用子技能：通过 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 逐项实施本计划。各步骤使用复选框（`- [ ]`）跟踪进度。

**目标：** 在继续功能开发或重构之前，消除当前暴露的认证、凭证、CORS 和任意查询风险。

**架构方案：** 保持 Sa-Token 作为唯一的 HTTP 认证边界；将环境秘密移出 Git 跟踪的资源；使用可配置的白名单替代通配 CORS；在每条查询校验和执行路径之前设置纯 SQL 防护器。通过有针对性的单元测试和 MVC 切片测试锁定安全行为，防止后续重构在无意中重新开放访问。

**技术栈：** Java 21、Spring Boot 4.0.6、Spring MVC Test、Sa-Token 1.45.0、JSqlParser 4.9、JUnit 5、Mockito、Maven 3.9.11。

## 全局约束

- 保持现有 `/api/v1` API 结构和响应封装不变。
- 保持 `/api/v1/auth/login`、`/api/v1/auth/captcha` 和 `/api/v1/auth/refresh` 公开；所有元数据、SQL、模型及用户信息端点均须登录后访问。
- 严禁记录明文密码、密码哈希、访问令牌、刷新令牌、验证码值或数据库凭证。
- SQL 校验和执行只接受一条只读 `SELECT` 语句；结果最多返回 1000 行，执行时间最长 60 秒。
- 保持 Windows Codex 沙箱启用；依赖下载和常驻开发服务器使用范围受限的已批准命令，不使用完全访问模式。
- 本阶段不引入新的安全框架，也不替换 Sa-Token。

---

## 文件结构

- `backend/src/main/java/com/selfmodeling/config/SaTokenConfig.java`：负责维护公开路由白名单。
- `backend/src/main/java/com/selfmodeling/service/impl/AuthServiceImpl.java`：执行用户认证，同时避免在日志中暴露秘密。
- `backend/src/main/java/com/selfmodeling/config/CorsProperties.java`：类型化的 CORS 白名单配置。
- `backend/src/main/java/com/selfmodeling/config/CorsConfig.java`：根据白名单创建 CORS 过滤器。
- `backend/src/main/java/com/selfmodeling/service/sql/ReadOnlySqlGuard.java`：解析并拒绝不安全或堆叠的 SQL。
- `backend/src/main/java/com/selfmodeling/service/impl/SqlServiceImpl.java`：校验并执行受保护、受限制的查询。
- `backend/src/main/resources/application.yml`：不含秘密的应用配置和 CORS 默认值。
- `backend/src/main/resources/datasource.yml`：仅保留由环境变量驱动的数据源配置。
- `backend/config/datasource-local.example.yml`：不含凭证的安全本地配置模板。
- `.gitignore`：排除真实的本地数据源覆盖文件。
- `README.md`：记录安全的本地配置方式和验证命令。
- `backend/src/test/java/com/selfmodeling/config/ApiAuthenticationBoundaryTest.java`：证明 SQL 和元数据 API 会拒绝匿名请求。
- `backend/src/test/java/com/selfmodeling/config/CorsConfigTest.java`：证明只有获准来源能够收到 CORS 响应头。
- `backend/src/test/java/com/selfmodeling/service/sql/ReadOnlySqlGuardTest.java`：证明系统只接受一条安全的 `SELECT` 语句。

---

### 任务 1：通过认证保护 SQL 和元数据 API

**文件：**
- 修改：`backend/src/main/java/com/selfmodeling/config/SaTokenConfig.java:15-23`
- 创建：`backend/src/test/java/com/selfmodeling/config/ApiAuthenticationBoundaryTest.java`

**接口：**
- 使用：Sa-Token 现有的 `StpUtil.checkLogin()` 拦截器。
- 产出：仅包含认证引导端点和 `/error` 的公开路由白名单。

- [ ] **步骤 1：编写预期失败的 MVC 认证边界测试**

```java
package com.selfmodeling.config;

import com.selfmodeling.controller.MetadataController;
import com.selfmodeling.controller.SqlController;
import com.selfmodeling.exception.GlobalExceptionHandler;
import com.selfmodeling.service.MetadataService;
import com.selfmodeling.service.SqlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {SqlController.class, MetadataController.class})
@Import({SaTokenConfig.class, GlobalExceptionHandler.class})
class ApiAuthenticationBoundaryTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SqlService sqlService;

    @MockitoBean
    private MetadataService metadataService;

    @Test
    void anonymousSqlExecutionIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/sql/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sql\":\"SELECT 1\",\"dataSourceId\":\"master\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void anonymousMetadataAccessIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/metadata/datasources"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }
}
```

- [ ] **步骤 2：运行测试，确认当前排除规则会导致测试失败**

在 `backend` 目录运行：

```powershell
& 'D:\apache-maven\bin\mvn.cmd' -s '..\..\.maven-settings.xml' -Dtest=ApiAuthenticationBoundaryTest test
```

预期：两个请求都会到达各自的控制器，而不是返回 HTTP 401。

- [ ] **步骤 3：移除 SQL 和元数据接口的排除规则**

将拦截器配置调整为：

```java
registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
        .addPathPatterns("/api/**")
        .excludePathPatterns("/api/v1/auth/login")
        .excludePathPatterns("/api/v1/auth/captcha")
        .excludePathPatterns("/api/v1/auth/refresh")
        .excludePathPatterns("/error");
```

- [ ] **步骤 4：运行认证测试**

运行步骤 2 中的命令。

预期：2 项测试通过；两个匿名请求均返回 HTTP 401，响应体中的状态码也为 401。

- [ ] **步骤 5：提交认证边界变更**

```powershell
git add backend/src/main/java/com/selfmodeling/config/SaTokenConfig.java backend/src/test/java/com/selfmodeling/config/ApiAuthenticationBoundaryTest.java
git commit -m "security: require authentication for data APIs"
```

---

### 任务 2：从源码和日志中移除凭证与认证秘密

**文件：**
- 修改：`backend/src/main/java/com/selfmodeling/service/impl/AuthServiceImpl.java:26-57`
- 修改：`backend/src/main/resources/application.yml`
- 修改：`backend/src/main/resources/datasource.yml`
- 创建：`backend/config/datasource-local.example.yml`
- 修改：`.gitignore`
- 修改：`README.md`

**接口：**
- 使用：环境变量 `MYSQL_URL`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`、`POSTGRES_URL`、`POSTGRES_USERNAME` 和 `POSTGRES_PASSWORD`。
- 产出：继续提供相同的 `master`、`postgres` 和 `sqlite` 数据源 ID，但不再跟踪秘密。

- [ ] **步骤 1：添加源码级回归检查，禁止记录秘密**

在仓库根目录运行：

```powershell
$matches = rg -n 'password:\s*\[|storedPassword|request\.getPassword\(\).*log|password:\s*[^$]' backend/src/main backend/config
if ($matches) { $matches; exit 1 }
```

变更前预期：在 `AuthServiceImpl.java` 和 `datasource.yml` 中发现匹配项。

- [ ] **步骤 2：移除明文密码和密码哈希日志**

严格应用以下日志差异：

```diff
-        log.info("验证密码 - 输入密码: [{}], 数据库密码: [{}]", rawPassword, storedPassword);
         // 正常的 BCrypt 验证
         try {
             boolean result = BCrypt.checkpw(rawPassword, storedPassword);
-            log.info("BCrypt 验证结果: {}", result);
             return result;
         } catch (Exception e) {
-            log.error("BCrypt 验证异常", e);
+            log.error("BCrypt password verification failed", e);
             return false;
         }
@@
-        log.info("尝试登录 - username: [{}], password: [{}]", request.getUsername(), request.getPassword());
+        log.info("Login attempt for username={}", request.getUsername());
```

- [ ] **步骤 3：将已跟踪的数据源值改为环境变量占位符**

在 `backend/src/main/resources/datasource.yml` 中严格应用以下替换；所有连接池和 SQLite 配置行须逐字节保持不变：

```diff
         master:
-          url: jdbc:mysql://localhost:3306/test
+          url: ${MYSQL_URL:jdbc:mysql://localhost:3306/test}
           driver-class-name: com.mysql.cj.jdbc.Driver
-          username: rebuild
+          username: ${MYSQL_USERNAME:}
+          password: ${MYSQL_PASSWORD:}
@@
         postgres:
-          url: jdbc:postgresql://localhost:5432/test?currentSchema=public
+          url: ${POSTGRES_URL:jdbc:postgresql://localhost:5432/test?currentSchema=public}
           driver-class-name: org.postgresql.Driver
-          username: cplcower
+          username: ${POSTGRES_USERNAME:}
+          password: ${POSTGRES_PASSWORD:}
```

对于两个密码字段，直接将当前已跟踪的值替换为上方对应的 `${MYSQL_PASSWORD:}` 或 `${POSTGRES_PASSWORD:}`；不要在计划、终端输出或提交中复述被移除的值。

- [ ] **步骤 4：添加可选的外部本地覆盖配置**

将 `application.yml` 中的导入配置改为：

```yaml
spring:
  config:
    import:
      - optional:classpath:datasource.yml
      - optional:file:./config/datasource-local.yml
```

添加以下忽略规则：

```gitignore
backend/config/datasource-local.yml
```

创建 `backend/config/datasource-local.example.yml`，其中只引用变量，不包含真实凭证：

```yaml
spring:
  datasource:
    dynamic:
      datasource:
        master:
          username: ${MYSQL_USERNAME}
          password: ${MYSQL_PASSWORD}
        postgres:
          username: ${POSTGRES_USERNAME}
          password: ${POSTGRES_PASSWORD}
```

- [ ] **步骤 5：记录安全的本地启动方式**

将以下 PowerShell 示例添加到 `README.md`：

```powershell
$mysqlCredential = Get-Credential -Message 'Local MySQL application account'
$postgresCredential = Get-Credential -Message 'Local PostgreSQL application account'
$env:MYSQL_URL = 'jdbc:mysql://localhost:3306/test'
$env:MYSQL_USERNAME = $mysqlCredential.UserName
$env:MYSQL_PASSWORD = $mysqlCredential.GetNetworkCredential().Password
$env:POSTGRES_URL = 'jdbc:postgresql://localhost:5432/test?currentSchema=public'
$env:POSTGRES_USERNAME = $postgresCredential.UserName
$env:POSTGRES_PASSWORD = $postgresCredential.GetNetworkCredential().Password
& 'D:\apache-maven\bin\mvn.cmd' spring-boot:run
```

- [ ] **步骤 6：记录延期的数据库凭证轮换风险**

本轮不执行凭证轮换。将“曾出现在 Git 历史中的数据库凭证尚未轮换”记录为明确的发布阻断风险；生产部署前仍须使用 MySQL 和 PostgreSQL 管理工具为两个应用账户设置新密码，只更新本地环境变量或 `backend/config/datasource-local.yml`，并验证旧凭证已无法连接。不得将新旧密码写入仓库、提交消息、终端记录或问题描述。

- [ ] **步骤 7：重新执行禁用日志扫描并打包后端**

```powershell
$authLogMatches = rg -n 'log\.[^(]+\([^\r\n]*(getPassword\(|rawPassword|storedPassword)' backend/src/main
if ($authLogMatches) { $authLogMatches; exit 1 }

$unsafeYamlCredentials = Get-ChildItem `
    backend/src/main/resources/datasource.yml, `
    backend/config/datasource-local.example.yml |
    ForEach-Object {
        $file = $_.FullName
        $lineNumber = 0
        Get-Content -Encoding utf8 $file | ForEach-Object {
            $lineNumber++
            if ($_ -notmatch '^\s*#' -and
                $_ -match '^\s*(username|password):\s*(.+)$' -and
                $Matches[2] -notmatch '^\$\{') {
                "$(Split-Path $file -Leaf):$lineNumber"
            }
        }
    }
if ($unsafeYamlCredentials) { $unsafeYamlCredentials; exit 1 }

& 'D:\apache-maven\bin\mvn.cmd' -s '..\..\.maven-settings.xml' -DskipTests package
```

预期：扫描不输出任何内容，Maven 报告 `BUILD SUCCESS`。

- [ ] **步骤 8：提交凭证治理变更**

```powershell
git add .gitignore README.md backend/src/main/java/com/selfmodeling/service/impl/AuthServiceImpl.java backend/src/main/resources/application.yml backend/src/main/resources/datasource.yml backend/config/datasource-local.example.yml
git commit -m "security: remove secrets from source and logs"
```

---

### 任务 3：使用白名单替代带凭证的通配 CORS

**文件：**
- 创建：`backend/src/main/java/com/selfmodeling/config/CorsProperties.java`
- 修改：`backend/src/main/java/com/selfmodeling/config/CorsConfig.java:15-30`
- 修改：`backend/src/main/resources/application.yml`
- 创建：`backend/src/test/java/com/selfmodeling/config/CorsConfigTest.java`

**接口：**
- 使用：`app.cors.allowed-origins`，其值为精确来源列表。
- 产出：`CorsFilter corsFilter(CorsProperties properties)`。

- [ ] **步骤 1：编写 CORS 测试**

```java
package com.selfmodeling.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class CorsConfigTest {

    @Test
    void allowsConfiguredDevelopmentOrigin() throws Exception {
        CorsFilter filter = new CorsConfig().corsFilter(
                new CorsProperties(List.of("http://127.0.0.1:5173")));
        MockHttpServletRequest request = preflight("http://127.0.0.1:5173");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertEquals("http://127.0.0.1:5173",
                response.getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    void rejectsUnconfiguredOrigin() throws Exception {
        CorsFilter filter = new CorsConfig().corsFilter(
                new CorsProperties(List.of("http://127.0.0.1:5173")));
        MockHttpServletRequest request = preflight("https://evil.example");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertNull(response.getHeader("Access-Control-Allow-Origin"));
    }

    private MockHttpServletRequest preflight(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/captcha");
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", "GET");
        return request;
    }
}
```

- [ ] **步骤 2：运行测试，确认类型化配置尚不存在**

```powershell
& 'D:\apache-maven\bin\mvn.cmd' -s '..\..\.maven-settings.xml' -Dtest=CorsConfigTest test
```

预期：由于 `CorsProperties` 和新的过滤器方法签名尚不存在，测试编译失败。

- [ ] **步骤 3：添加类型化 CORS 属性**

```java
package com.selfmodeling.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {
    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
```

- [ ] **步骤 4：根据精确来源构建过滤器**

将 `CorsConfig` 替换为：

```java
package com.selfmodeling.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter(CorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(properties.allowedOrigins());
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
```

- [ ] **步骤 5：添加安全的开发环境默认值**

在 `application.yml` 中添加：

```yaml
app:
  cors:
    allowed-origins:
      - ${FRONTEND_ORIGIN:http://127.0.0.1:5173}
      - http://localhost:5173
```

- [ ] **步骤 6：运行 CORS 测试并提交**

```powershell
& 'D:\apache-maven\bin\mvn.cmd' -s '..\..\.maven-settings.xml' -Dtest=CorsConfigTest test
git add backend/src/main/java/com/selfmodeling/config/CorsConfig.java backend/src/main/java/com/selfmodeling/config/CorsProperties.java backend/src/main/resources/application.yml backend/src/test/java/com/selfmodeling/config/CorsConfigTest.java
git commit -m "security: restrict credentialed cors origins"
```

预期：2 项测试通过。

---

### 任务 4：强制执行单语句、受限制的只读 SQL

**文件：**
- 创建：`backend/src/main/java/com/selfmodeling/service/sql/ReadOnlySqlGuard.java`
- 创建：`backend/src/test/java/com/selfmodeling/service/sql/ReadOnlySqlGuardTest.java`
- 修改：`backend/src/main/java/com/selfmodeling/service/impl/SqlServiceImpl.java:50-146`
- 修改：`backend/src/main/java/com/selfmodeling/service/impl/SqlServiceImpl.java:529-533`

**接口：**
- 产出：`String ReadOnlySqlGuard.validate(String sql)`，返回规范化 SQL 或抛出 `IllegalArgumentException`。
- 使用：`SqlServiceImpl.validateSql` 和 `SqlServiceImpl.executeQuery` 均调用该防护器。

- [ ] **步骤 1：编写 SQL 防护器测试**

```java
package com.selfmodeling.service.sql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReadOnlySqlGuardTest {

    private final ReadOnlySqlGuard guard = new ReadOnlySqlGuard();

    @Test
    void acceptsPlainSelect() {
        assertEquals("SELECT 1", guard.validate(" SELECT 1; "));
    }

    @Test
    void acceptsCommonTableExpressionSelect() {
        assertEquals("WITH x AS (SELECT 1 AS id) SELECT id FROM x",
                guard.validate("WITH x AS (SELECT 1 AS id) SELECT id FROM x"));
    }

    @Test
    void rejectsMutation() {
        assertThrows(IllegalArgumentException.class,
                () -> guard.validate("DELETE FROM sys_user"));
    }

    @Test
    void rejectsStackedStatements() {
        assertThrows(IllegalArgumentException.class,
                () -> guard.validate("SELECT 1; DELETE FROM sys_user"));
    }

    @Test
    void rejectsFileAndDelayFunctions() {
        assertThrows(IllegalArgumentException.class,
                () -> guard.validate("SELECT LOAD_FILE('/etc/passwd')"));
        assertThrows(IllegalArgumentException.class,
                () -> guard.validate("SELECT SLEEP(10)"));
        assertThrows(IllegalArgumentException.class,
                () -> guard.validate("SELECT pg_sleep(10)"));
    }
}
```

- [ ] **步骤 2：运行测试，确认因防护器尚不存在而失败**

```powershell
& 'D:\apache-maven\bin\mvn.cmd' -s '..\..\.maven-settings.xml' -Dtest=ReadOnlySqlGuardTest test
```

预期：由于 `ReadOnlySqlGuard` 尚不存在，测试编译失败。

- [ ] **步骤 3：实现纯 SQL 防护器**

```java
package com.selfmodeling.service.sql;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class ReadOnlySqlGuard {

    private static final Pattern FORBIDDEN_SELECT_CAPABILITIES = Pattern.compile(
            "(?is)\\b(INTO\\s+(OUTFILE|DUMPFILE)|LOAD_FILE\\s*\\(|SLEEP\\s*\\(|BENCHMARK\\s*\\(|PG_SLEEP\\s*\\()"
    );

    public String validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL must not be blank");
        }

        String normalized = sql.trim();
        if (normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }

        try {
            Statements parsed = CCJSqlParserUtil.parseStatements(normalized);
            List<Statement> statements = parsed.getStatements();
            if (statements.size() != 1 || !(statements.get(0) instanceof Select)) {
                throw new IllegalArgumentException("Exactly one SELECT statement is required");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("SQL syntax is invalid", e);
        }

        String upper = normalized.toUpperCase(Locale.ROOT);
        if (FORBIDDEN_SELECT_CAPABILITIES.matcher(upper).find()) {
            throw new IllegalArgumentException("The SELECT uses a forbidden capability");
        }
        return normalized;
    }
}
```

- [ ] **步骤 4：注入防护器，并在校验和执行路径中使用**

添加字段：

```java
@Autowired
private ReadOnlySqlGuard readOnlySqlGuard;
```

在 `validateSql` 开头，使用以下逻辑替代前缀匹配：

```java
final String safeSql;
try {
    safeSql = readOnlySqlGuard.validate(sql);
} catch (IllegalArgumentException e) {
    result.put("valid", false);
    result.put("message", e.getMessage());
    return result;
}
```

严格应用以下 `EXPLAIN` 替换：

```diff
-                    explainSql = "EXPLAIN QUERY PLAN " + sql;
+                    explainSql = "EXPLAIN QUERY PLAN " + safeSql;
@@
-                    explainSql = "EXPLAIN " + sql;
+                    explainSql = "EXPLAIN " + safeSql;
```

将整个 `executeQuery` 方法替换为：

```java
@Override
public Map<String, Object> executeQuery(String sql, int limit, String dataSourceId) {
    Map<String, Object> result = new HashMap<>();
    final String safeSql;
    try {
        safeSql = readOnlySqlGuard.validate(sql);
    } catch (IllegalArgumentException e) {
        result.put("success", false);
        result.put("message", e.getMessage());
        return result;
    }

    int safeLimit = Math.max(1, Math.min(limit, 1000));
    try {
        JdbcTemplate targetJdbcTemplate = metadataService.getJdbcTemplateByDataSourceId(dataSourceId);
        if (targetJdbcTemplate == null) {
            result.put("success", false);
            result.put("message", "未知的数据源: " + dataSourceId);
            return result;
        }
        DataSource dataSource = targetJdbcTemplate.getDataSource();
        if (dataSource == null) {
            log.error("数据源 {} 配置异常，无法获取数据库连接", dataSourceId);
            result.put("success", false);
            result.put("message", "数据源配置异常，无法获取数据库连接");
            return result;
        }

        try (Connection conn = dataSource.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            conn.setReadOnly(true);
            stmt.setMaxRows(safeLimit);
            stmt.setQueryTimeout(60);
            try (ResultSet rs = stmt.executeQuery(safeSql)) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    columns.add(metaData.getColumnName(i));
                }
                result.put("columns", columns);

                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnName(i);
                        Object value = rs.getObject(i);
                        if (value instanceof Timestamp) {
                            value = value.toString();
                        }
                        row.put(columnName, value);
                    }
                    rows.add(row);
                }
                result.put("rows", rows);
                result.put("total", rows.size());
                result.put("success", true);
                result.put("message", "查询成功");
            }
        }
    } catch (SQLException e) {
        log.error("SQL 执行失败: {}", e.getMessage());
        result.put("success", false);
        result.put("message", "SQL 执行失败");
    }
    return result;
}
```

两个调用点均改用防护器后，删除 `isDangerousOperation`。

- [ ] **步骤 5：运行 SQL 防护器和认证回归测试**

```powershell
& 'D:\apache-maven\bin\mvn.cmd' -s '..\..\.maven-settings.xml' '-Dtest=ReadOnlySqlGuardTest,ApiAuthenticationBoundaryTest' test
```

预期：所有 SQL 防护用例以及两个匿名访问用例均通过。

- [ ] **步骤 6：提交 SQL 防护执行变更**

```powershell
git add backend/src/main/java/com/selfmodeling/service/sql/ReadOnlySqlGuard.java backend/src/main/java/com/selfmodeling/service/impl/SqlServiceImpl.java backend/src/test/java/com/selfmodeling/service/sql/ReadOnlySqlGuardTest.java
git commit -m "security: guard and bound sql execution"
```

---

### 任务 5：执行 P0 安全发布门禁

**文件：**
- 修改：`README.md`

**接口：**
- 使用：任务 1 至 4 产出的认证边界、无秘密配置、CORS 白名单和 SQL 防护器。
- 产出：面向维护者、可重复执行的发布检查清单。

- [ ] **步骤 1：运行全部后端测试**

```powershell
& 'D:\apache-maven\bin\mvn.cmd' -s '..\..\.maven-settings.xml' test
```

预期：构建结果为 `BUILD SUCCESS`，并包含 `ApiAuthenticationBoundaryTest`、`CorsConfigTest` 和 `ReadOnlySqlGuardTest`。

- [ ] **步骤 2：打包应用**

```powershell
& 'D:\apache-maven\bin\mvn.cmd' -s '..\..\.maven-settings.xml' package
```

预期：重新生成 `backend/target/self-modeling-platform-1.0.0.jar`，Maven 报告 `BUILD SUCCESS`。

- [ ] **步骤 3：运行带认证的 API 冒烟测试**

使用本地环境变量启动后端，然后验证：

```text
GET  /api/v1/auth/captcha                 -> 无令牌时返回 200
POST /api/v1/sql/execute                  -> 无令牌时返回 401
GET  /api/v1/metadata/datasources         -> 无令牌时返回 401
POST /api/v1/auth/login                   -> 使用有效凭证和验证码时返回 200
POST /api/v1/sql/execute                  -> 使用有效令牌执行 SELECT 1 时返回 200
POST /api/v1/sql/execute                  -> 对堆叠语句或修改型 SQL 返回业务错误
```

- [ ] **步骤 4：将发布门禁写入 README**

在“安全验证”章节记录步骤 3 中的六项检查，同时加入 Maven 测试命令，并明确规定：生产部署不得使用带凭证的通配 CORS，也不得使用数据库所有者凭证执行用户编写的查询。

- [ ] **步骤 5：提交发布门禁文档**

```powershell
git add README.md
git commit -m "docs: add security release gate"
```

---

## 自审结果

- 规格覆盖：认证、凭证治理、CORS、SQL 安全、测试、打包和运行时验证均已分配到具体任务。
- 延期项检查：数据库凭证轮换是唯一明确延期的高风险事项；完成轮换前不得宣称 P0 安全工作全部关闭。运行凭证值按设计保留在 Git 之外。
- 类型一致性：`ReadOnlySqlGuard.validate(String)` 和 `CorsProperties.allowedOrigins()` 均只定义一次，并以相同签名被调用。
