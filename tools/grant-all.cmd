@echo off
rem LocationShare: выдать все разрешения подключенному телефону
rem (USB-отладка должна быть включена и телефон авторизован)
set ADB=C:\Users\afilatov\platform-tools\adb.exe
set PKG=com.locationshare.app

%ADB% shell pm grant %PKG% android.permission.ACCESS_FINE_LOCATION
%ADB% shell pm grant %PKG% android.permission.ACCESS_COARSE_LOCATION
rem "Всё время": без него FGS, стартованный из фона, теряет доступ к геолокации
rem (на Android ниже 10 команда просто напечатает Unknown permission — это не ошибка)
%ADB% shell pm grant %PKG% android.permission.ACCESS_BACKGROUND_LOCATION
%ADB% shell pm grant %PKG% android.permission.POST_NOTIFICATIONS
%ADB% shell pm grant %PKG% android.permission.CAMERA
%ADB% shell appops set %PKG% REQUEST_INSTALL_PACKAGES allow
%ADB% shell dumpsys deviceidle whitelist +%PKG%
%ADB% shell cmd appops set %PKG% START_FOREGROUND allow
%ADB% shell cmd appops set %PKG% RUN_ANY_IN_BACKGROUND allow

echo.
echo Готово: разрешения выданы. Не забудьте на Xiaomi/POCO:
echo   Настройки -^> Приложения -^> LocationShare -^> Контроль активности: "Нет ограничений", Автозапуск: вкл.
pause
