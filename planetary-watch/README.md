# Planetary Watch v0.1 — исследовательский MVP

Windows 10/11 x64, WebView2. Распакуйте PlanetaryWatch_Portable.zip целиком и запустите PlanetaryWatch.exe. При необходимости установите Microsoft Edge WebView2 Runtime. Не копируйте EXE отдельно от каталога _internal.

Сборка локально: Python 3.12, pip install pywebview pyinstaller pillow astropy tzdata; python planetary_watch.py --self-test; python planetary_watch.py --icon; python planetary_watch.py. Для упаковки используйте .github/workflows/planetary-watch.yml.

Источники: NASA CAD (до 365 дней, до 0.05 AU), Sentry, Scout, Fireball (30 дней), SBDB, Horizons и исходная таблица ESA risk list. Запросы последовательны. Состояние, кэш и ежедневные HTML/JSON отчёты: %LOCALAPPDATA%/PlanetaryWatch. Отчёт при работающей программе после 09:00 Asia/Yekaterinburg. При закрытии окна мониторинг прекращается.

Визуализация выбранного объекта: геоцентрические ICRF векторы Horizons, TDB, км/км/с. Кубическая интерполяция Эрмита в диапазоне эфемерид, без экстраполяции. Гравитационные возмущения учтены в опубликованной траектории, а показанное ускорение Земли — точечная модель GM/r². Номинальная траектория не является моделью риска. Показаны официальные данные риска без собственной вероятности. Параметры вращения доступны в сыром SBDB, если опубликованы. Коридоры падения только по ссылке ESA; приложение не определяет свои координаты.

Ограничения v0.1: нет самостоятельного N-body интегратора, полного каталога NEO/PHA, ансамблей ковариации, карты коридоров, вращения формы, параллельного отображения орбит, PDF, Windows-уведомлений, трея, автозапуска, установщика. 3D-проекция на Canvas вместо WebGL. Scout/ESA и остальные живые контракты требуют проверки на Windows с интернетом. Нет гарантии полного охвата. Не средство официального предупреждения.

Документация: https://ssd-api.jpl.nasa.gov/doc/ ; https://neo.ssa.esa.int/computer-access
