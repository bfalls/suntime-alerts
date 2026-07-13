@echo off
setlocal
pushd "%~dp0..\android"
call gradlew.bat :sky-banner-lab:run
popd
