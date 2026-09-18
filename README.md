# MoodCalendar

Android 情感日历：纪念日 / 倒数日 / 生日、自定义提醒、情感记录、蓝粉主题；二期支持 Supabase 登录、云同步与好友互动。

## 技术栈

- Kotlin + Jetpack Compose + Material 3
- Room / DataStore / Navigation / Coil
- AlarmManager 精确提醒
- Supabase（Auth + Postgres + Storage + RLS）

## 打开方式

用 Android Studio 打开本目录，同步 Gradle 后运行 `app` 模块。

要求：JDK 17 或 21、Android SDK 35、minSdk 26。

### 二期云端配置（可选）

未配置时仍可完整使用本地功能；社交与同步入口会提示配置/登录。

1. 在 [Supabase](https://supabase.com) 创建项目，开启 Email 登录。
3. 在 SQL Editor **依次**执行：
   - [`supabase/migrations/001_phase2_social.sql`](supabase/migrations/001_phase2_social.sql)
   - [`supabase/migrations/002_avatars_and_defaults.sql`](supabase/migrations/002_avatars_and_defaults.sql)
   - [`supabase/migrations/003_profile_email_search.sql`](supabase/migrations/003_profile_email_search.sql)
3. Auth 邮件与 URL（很重要）：
   - **Authentication → Providers → Email**：开启 **Confirm email**。
   - **Authentication → URL Configuration**：
     - **Site URL**：填 `https://localhost`（纯 App 验证码流程即可；**不要**填 `https://xxxx.supabase.co`，否则会出现 `requested path is invalid`）。
     - Redirect URLs 可留空或同样加 `https://localhost`（可选）。
   - **Authentication → Email Templates**：
     - Confirm signup：正文加入 `{{ .Token }}`（6 位验证码），引导用户回 App 输入，不要依赖邮件里的确认链接。
     - Reset password：若要在 App 内用验证码重置，同样加入 `{{ .Token }}`。
   - **邮件发不出去 / Error sending confirmation email**（很常见）：
     - 默认内置邮箱**仅供试用**，有严格小时限额，多人注册很快就会失败。
     - 请到 **Authentication → Emails → SMTP Settings** 配置自定义 SMTP（推荐 [Resend](https://resend.com) / SendGrid）。
     - 也可临时关闭 **Confirm email**（仅测试用，注册将不再需要验证码）。
     - 到 **Logs → Auth** 查看具体发送失败原因。
4. 复制 `local.properties.example` 为 `local.properties`，填写：

```properties
supabase.url=https://xxxx.supabase.co
supabase.anonKey=your-anon-key
```

5. Sync Gradle 后重新编译安装。

## 功能入口

| Tab | 内容 |
|-----|------|
| 日历 | 月视图，事件/心情标记，点日期进详情 |
| 事件 | 纪念日 / 倒数日 / 生日列表与编辑 |
| 感情 | 情感记录；右上角进入好友动态（需登录） |
| 我的 | 账号登录/同步、好友、蓝粉主题、通知权限 |

### 登录与同步

- 未登录：一期全部本地功能可用。
- 注册：邮箱验证码；新账号默认头像。
- 登录后：可改头像 / 昵称 / 密码；本地心情、事件、设置后台同步；退出保留本地数据。
- 忘记密码：发送重置邮件（验证码或链接，取决于模板）。
- 社交：好友申请（好友码）、动态 Feed、点赞 / 评论 / 收藏；未登录使用时提示去登录。
