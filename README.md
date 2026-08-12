# MoodCalendar

Android 情感日历（一期本地版）：纪念日 / 倒数日 / 生日、自定义提醒、情感记录、蓝粉主题。

## 技术栈

- Kotlin + Jetpack Compose + Material 3
- Room / DataStore / Navigation / Coil
- AlarmManager 精确提醒

## 打开方式

用 Android Studio 打开本目录，同步 Gradle 后运行 `app` 模块。

要求：JDK 17、Android SDK 35、minSdk 26。

## 功能入口

| Tab | 内容 |
|-----|------|
| 日历 | 月视图，事件/心情标记，点日期进详情 |
| 事件 | 纪念日 / 倒数日 / 生日列表与编辑 |
| 我的 | 蓝粉主题、通知与精确闹钟权限 |

情感记录支持文字、表情、本地图片；可见性字段已预留（默认全部好友），社交能力二期再做。
